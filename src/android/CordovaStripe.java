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
    public static final String TAG = "CORDOVA_STRIPE_PLUGIN";

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

            this.createPaymentSession(secretKey, mode, currency, amount, paymentSuccessUrl, paymentCancelUrl, priceId,
                    customerID, callbackContext);
            return true;
        }

        return false;
    }

    private void createPaymentSession(String secretKey, String mode, String currency, int amount,
            String paymentSuccessUrl, String paymentCancelUrl, String priceId, String customerID,
            CallbackContext callbackContext) {
        if (mode.toLowerCase().equals("subscription")) {
            createSubscriptionLink(secretKey, priceId, customerID, paymentSuccessUrl, paymentCancelUrl,
                    callbackContext);
        } else {
            createPaymentLink(secretKey, amount, currency, paymentSuccessUrl, paymentCancelUrl,
                    callbackContext);
        }
    }

    private void createPaymentLink(String secretKey, int unitAmount, String currency, String paymentSuccessUrl,
            String paymentCancelUrl,
            CallbackContext callbackContext) {
        String productName = "Stripe Product Checkout";
        String requestBody = "line_items[0][price_data][unit_amount]=" + unitAmount +
                "&line_items[0][price_data][currency]=" + currency +
                "&line_items[0][price_data][product_data][name]=" + productName
                + "&line_items[0][quantity]=1&mode=payment&success_url="
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

    private void createSubscriptionLink(String secretKey, String priceId, String customerID,
            String paymentSuccessUrl, String paymentCancelUrl, CallbackContext callbackContext) {
        String requestBody = "line_items[0][price]=" + priceId
                + "&line_items[0][quantity]=1&mode=subscription&success_url=" + paymentSuccessUrl + "&customer="
                + customerID;
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
            HttpURLConnection conn = null;
            try {
                // Initialize the connection
                URL url = new URL(STRIPE_API_URL + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("Authorization", "Bearer " + secretKey);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // Send POST data if necessary
                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                    try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                        wr.writeBytes(requestBody);
                        wr.flush();
                    }
                }

                // Get the response code
                int responseCode = conn.getResponseCode();
                InputStreamReader streamReader;

                // Determine if the response is an error based on HTTP status code
                if (responseCode >= 200 && responseCode < 300) {
                    streamReader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                } else {
                    // Handle HTTP errors
                    streamReader = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8);
                }

                // Read the response
                BufferedReader in = new BufferedReader(streamReader);
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                if (responseCode >= 200 && responseCode < 300) {
                    // If response is successful
                    return response.toString();
                } else {
                    // Parse the error response and extract the message
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if (jsonResponse.has("error")) {
                        JSONObject errorObject = jsonResponse.getJSONObject("error");
                        String errorMessage = errorObject.getString("message");
                        callbackContext.error(errorMessage);
                    } else {
                        callbackContext.error(response.toString());
                    }
                    return null;
                }

            } catch (Exception e) {
                callbackContext.error("Network error: " + e.getMessage());
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
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
