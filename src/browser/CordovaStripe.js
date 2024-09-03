// External Libraries
// Applied axios to the plugin
var script = document.createElement('script');
script.src = "https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js";
document.head.appendChild(script);

// CONST
const STRIPE_API_URL = 'https://api.stripe.com/v1';

// Init Var
var browserMethod = {};

// API CALL
const apiCall = async (path, body, header, method, success, error) => {
    // Map the request method
    if(method === "POST") {
        return await axios.post(STRIPE_API_URL+path, body, {headers: header, withCredentials: true}).then(success).catch(error);
    } else {
        return await axios.get(STRIPE_API_URL+path, body, {headers: header, withCredentials: true}).then(success).catch(error);
    }
}

const convertObjectToEncoded = (object) => {
    var formBody = [];

    for (var property in object) {
        var encodedKey = encodeURIComponent(property);
        var encodedValue = encodeURIComponent(object[property]);
        formBody.push(encodedKey + "=" + encodedValue);
    }
    formBody = formBody.join("&");

    return formBody;
}

// Create a new payment link 
const createPaymentLink = async (secretKey, unitAmount, currency, paymentSuccessUrl, paymentCancelUrl) => {
    var requestBody = {
        'line_items[0][price_data][unit_amount]': unitAmount,
        'line_items[0][price_data][currency]': currency,
        'line_items[0][price_data][product_data][name]': 'Stripe Product Checkout',
        'line_items[0][quantity]': 1,
        mode: 'payment',
        success_url: paymentSuccessUrl || '',
    };
    var responseObject = {};

    if(paymentCancelUrl !== null) {
        requestBody.cancel_url = paymentCancelUrl;
    }

    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }

    // Call Stripe API
    await apiCall('/checkout/sessions', requestBody, requestHeader, 'POST', 
        (data) => {
            // get payment link
            responseObject.paymentLink = data.data.url;
            responseObject.message = "Success create payment link";
        } 
    , 
        (err) => {
            responseObject.paymentLink = null;
            responseObject.message = "Failed create payment link";

            // Handling error from stripe side
            if (err.response) {
                if(err.response.data.error.message) {
                    responseObject.message = err.response.data.error.message;
                } 
            }
        }
    );

    return responseObject;
}

// Create a new subscription link 
const createSubscriptionLink = async (secretKey, priceId, customerID, paymentSuccessUrl, paymentCancelUrl, error) => {
    var requestBody = {
        'line_items[0][price]': priceId,
        'line_items[0][quantity]': 1,
        mode: 'subscription',
        success_url: paymentSuccessUrl || '',
        customer: customerID
    };
    var responseObject = {};

    if(paymentCancelUrl !== null) {
        requestBody.cancel_url = paymentCancelUrl;
    }

    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }

    // Call Stripe API
    await apiCall('/checkout/sessions', requestBody, requestHeader, 'POST', 
        (data) => {
            // get subscription link
            responseObject.paymentLink = data.data.url;
            responseObject.message = "Success create subscription link";
        } 
    , 
        (err) => {
            responseObject.paymentLink = null;
            responseObject.message = "Failed create subscription link";

            // Handling error from stripe side
            if (err.response) {
                if(err.response.data.error.message) {
                    responseObject.message = err.response.data.error.message;
                } 
            }
        }
    );

    return responseObject;
}

// Create new payment session
browserMethod.createPaymentSession = async (success, error, args) => {
    var secretKey = args[0];
    var mode = args[1];
    var currency = args[2];
    var amount = args[3];
    var paymentSuccessUrl = args[4];
    var paymentCancelUrl = args[5] || null;
    var priceId = args[6] || null;
    var customerID = args[7] || null;

    if(paymentSuccessUrl === null) {
        error("Please specify payment success URL");
    }

    if(mode !== 'subscription') {
        // createPaymentLink
        payment = await createPaymentLink(secretKey, amount, currency, paymentSuccessUrl, paymentCancelUrl);
    } else {
        payment  = await createSubscriptionLink(secretKey, priceId, customerID, paymentSuccessUrl, paymentCancelUrl);
    }

    if(payment.paymentLink === null) {
        error(payment.message);
    } else {
        success(payment.paymentLink);
    }
}

require('cordova/exec/proxy').add('CordovaStripe', browserMethod);