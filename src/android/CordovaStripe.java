package com.stevanstev.plugin;

import android.os.AsyncTask;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CordovaStripe extends CordovaPlugin {

    private static final String STRIPE_API_URL = "https://api.stripe.com/v1";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("createPaymentSession".equals(action)) {
            String secretKey = args.getString(0);
            String mode = args.getString(1);
            String currency = args.getString(2);
            int amount = args.getInt(3);
            String paymentSuccessUrl = args.getString(4);
            String paymentCancelUrl = args.optString(5, null);
            String priceId = args.optString(6, null);
            String customerID = args.optString(7, null);
            int itemQuantity = args.optInt(8, 1);

            this.createPaymentSession(secretKey, mode, currency, amount, paymentSuccessUrl, paymentCancelUrl, priceId,
                    customerID, itemQuantity, callbackContext);
            return true;
        }

        return false;
    }

    private void createPaymentSession(String secretKey, String mode, String currency, int amount,
            String paymentSuccessUrl, String paymentCancelUrl, String priceId, String customerID, int itemQuantity,
            CallbackContext callbackContext) {
        if (mode.equals("subscription")) {
            createSubscriptionLink(secretKey, priceId, customerID, itemQuantity, paymentSuccessUrl, paymentCancelUrl,
                    callbackContext);
        } else {
            createProduct(secretKey, "PayProduct", currency, amount, paymentSuccessUrl, paymentCancelUrl,
                    callbackContext);
        }
    }

    private void createProduct(String secretKey, String productName, String currency, int amount,
            String paymentSuccessUrl, String paymentCancelUrl, CallbackContext callbackContext) {
        new ApiTask(secretKey, "/products", "POST", "name=" + productName, response -> {
            try {
                JSONObject json = new JSONObject(response);
                String productId = json.getString("id");
                createPrice(secretKey, currency, amount, productId, paymentSuccessUrl, paymentCancelUrl,
                        callbackContext);
            } catch (JSONException e) {
                callbackContext.error("Failed to create product: " + e.getMessage());
            }
        }, callbackContext).execute();
    }

    private void createPrice(String secretKey, String currency, int amount, String productId, String paymentSuccessUrl,
            String paymentCancelUrl, CallbackContext callbackContext) {
        String requestBody = "unit_amount=" + amount + "&currency=" + currency + "&product=" + productId;
        new ApiTask(secretKey, "/prices", "POST", requestBody, response -> {
            try {
                JSONObject json = new JSONObject(response);
                String priceId = json.getString("id");
                createPaymentLink(secretKey, priceId, paymentSuccessUrl, paymentCancelUrl, callbackContext);
            } catch (JSONException e) {
                callbackContext.error("Failed to create price: " + e.getMessage());
            }
        }, callbackContext).execute();
    }

    private void createPaymentLink(String secretKey, String priceId, String paymentSuccessUrl, String paymentCancelUrl,
            CallbackContext callbackContext) {
        String requestBody = "line_items[0][price]=" + priceId + "&line_items[0][quantity]=1&mode=payment&success_url="
                + paymentSuccessUrl;
        if (paymentCancelUrl != null) {
            requestBody += "&cancel_url=" + paymentCancelUrl;
        }

        new ApiTask(secretKey, "/checkout/sessions", "POST", requestBody, response -> {
            try {
                JSONObject json = new JSONObject(response);
                String paymentLink = json.getString("url");
                callbackContext.success(paymentLink);
            } catch (JSONException e) {
                callbackContext.error("Failed to create payment link: " + e.getMessage());
            }
        }, callbackContext).execute();
    }

    private void createSubscriptionLink(String secretKey, String priceId, String customerID, int itemQuantity,
            String paymentSuccessUrl, String paymentCancelUrl, CallbackContext callbackContext) {
        String requestBody = "line_items[0][price]=" + priceId + "&line_items[0][quantity]=" + itemQuantity
                + "&mode=subscription&success_url=" + paymentSuccessUrl + "&customer=" + customerID;
        if (paymentCancelUrl != null) {
            requestBody += "&cancel_url=" + paymentCancelUrl;
        }

        new ApiTask(secretKey, "/checkout/sessions", "POST", requestBody, response -> {
            try {
                JSONObject json = new JSONObject(response);
                String paymentLink = json.getString("url");
                callbackContext.success(paymentLink);
            } catch (JSONException e) {
                callbackContext.error("Failed to create subscription link: " + e.getMessage());
            }
        }, callbackContext).execute();
    }

    private static class ApiTask extends AsyncTask<Void, Void, String> {
        private String secretKey;
        private String path;
        private String method;
        private String requestBody;
        private ResponseCallback callback;
        private CallbackContext callbackContext;

        ApiTask(String secretKey, String path, String method, String requestBody, ResponseCallback callback,
                CallbackContext callbackContext) {
            this.secretKey = secretKey;
            this.path = path;
            this.method = method;
            this.requestBody = requestBody;
            this.callback = callback;
            this.callbackContext = callbackContext;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(STRIPE_API_URL + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("Authorization", "Bearer " + secretKey);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                    try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                        wr.writeBytes(requestBody);
                        wr.flush();
                    }
                }

                int responseCode = conn.getResponseCode();
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                if (responseCode == 200) {
                    return response.toString();
                } else {
                    callbackContext.error("API error: " + response.toString());
                    return null;
                }

            } catch (Exception e) {
                callbackContext.error("Network error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                callback.onResponse(result);
            }
        }
    }

    private interface ResponseCallback {
        void onResponse(String response);
    }
}
