package com.stevanstev.plugin;

import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class CordovaStripe extends CordovaPlugin {

    private static final String STRIPE_API_URL = "https://api.stripe.com/v1";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("createPaymentSession".equals(action)) {
            this.createPaymentSession(args, callbackContext);
            return true;
        }
        return false;
    }

    private void createPaymentSession(JSONArray args, CallbackContext callbackContext) {
        try {
            String secretKey = args.getString(0);
            String mode = "payment";
            String currency = args.getString(2);
            int amount = args.getInt(3);
            String paymentSuccessUrl = args.getString(4);

            if (paymentSuccessUrl == null) {
                callbackContext.error("Please specify payment success URL");
                return;
            }

            cordova.getThreadPool().execute(() -> {
                try {
                    String productId = createProduct(secretKey, "Test Product");
                    if (productId == null) {
                        callbackContext.error("Failed to create product");
                        return;
                    }

                    String priceId = createPrice(secretKey, currency, amount, productId);
                    if (priceId == null) {
                        callbackContext.error("Failed to create price");
                        return;
                    }

                    String paymentLink = createPaymentLink(secretKey, priceId, mode, paymentSuccessUrl);
                    if (paymentLink == null) {
                        callbackContext.error("Failed to create payment link");
                        return;
                    }

                    callbackContext.success(paymentLink);
                } catch (Exception e) {
                    callbackContext.error("Error: " + e.getMessage());
                }
            });

        } catch (JSONException e) {
            callbackContext.error("JSON error: " + e.getMessage());
        }
    }

    private String createProduct(String secretKey, String productName) throws Exception {
        String endpoint = STRIPE_API_URL + "/products";
        String requestBody = "name=" + URLEncoder.encode(productName, "UTF-8");

        String response = apiCall(endpoint, requestBody, secretKey, "POST");
        if (response != null) {
            return parseJsonForId(response);
        }
        return null;
    }

    private String createPrice(String secretKey, String currency, int unitAmount, String productId) throws Exception {
        String endpoint = STRIPE_API_URL + "/prices";
        String requestBody = "unit_amount=" + unitAmount +
                "&currency=" + URLEncoder.encode(currency, "UTF-8") +
                "&product=" + URLEncoder.encode(productId, "UTF-8");

        String response = apiCall(endpoint, requestBody, secretKey, "POST");
        if (response != null) {
            return parseJsonForId(response);
        }
        return null;
    }

    private String createPaymentLink(String secretKey, String priceId, String mode, String paymentSuccessUrl)
            throws Exception {
        String endpoint = STRIPE_API_URL + "/checkout/sessions";
        String requestBody = "line_items[0][price]=" + URLEncoder.encode(priceId, "UTF-8") +
                "&line_items[0][quantity]=1" +
                "&mode=" + URLEncoder.encode(mode, "UTF-8") +
                "&success_url=" + URLEncoder.encode(paymentSuccessUrl, "UTF-8");

        String response = apiCall(endpoint, requestBody, secretKey, "POST");
        if (response != null) {
            return parseJsonForUrl(response);
        }
        return null;
    }

    private String apiCall(String endpoint, String requestBody, String secretKey, String method) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + secretKey);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        if (requestBody != null && !requestBody.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
            }
        }

        int responseCode = conn.getResponseCode();
        BufferedReader in;
        if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        }

        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.d("CordovaStripe", "API Response: " + response.toString());
            return response.toString();
        } else {
            Log.e("CordovaStripe",
                    "Failed API call with response code: " + responseCode + " and response: " + response.toString());
            return null;
        }
    }

    private String parseJsonForId(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getString("id");
        } catch (JSONException e) {
            Log.e("CordovaStripe", "JSON parsing error: " + e.getMessage());
            return null;
        }
    }

    private String parseJsonForUrl(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getString("url");
        } catch (JSONException e) {
            Log.e("CordovaStripe", "JSON parsing error: " + e.getMessage());
            return null;
        }
    }
}