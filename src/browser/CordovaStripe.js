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

// Create a new product 
const createProduct = async (secretKey, productName) => {
    var requestBody = {
        name: productName,
    };
    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }
    var responseObject = {};

    // Call Stripe API
    await apiCall('/products', requestBody, requestHeader, 'POST', 
        (data) => {
            // get product id
            responseObject.productId = data.data.id;
            responseObject.message = 'Success create product';
        } 
    , 
        (err) => {
            responseObject.productId = null;
            responseObject.message = err.data.error.message;

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

// Create a new price 
const createPrice = async (secretKey, currency, unitAmount, productId) => {
    var requestBody = {
        unit_amount: unitAmount,
        currency: currency,
        product: productId
    };
    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }
    var responseObject = {};

    // Call Stripe API
    await apiCall('/prices', requestBody, requestHeader, 'POST', 
        (data) => {
            // get price id
            responseObject.priceId = data.data.id;
            responseObject.message = "Success create price";
        } 
    , 
        (err) => {
            responseObject.priceId = null;
            responseObject.message = "Failed create price";

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

// Create a new payment link 
const createPaymentLink = async (secretKey, priceId, paymentSuccessUrl, paymentCancelUrl) => {
    var requestBody = {
        'line_items[0][price]': priceId,
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
const createSubscriptionLink = async (secretKey, priceId, customerID, itemQuantity, paymentSuccessUrl, paymentCancelUrl, error) => {
    var requestBody = {
        'line_items[0][price]': priceId,
        'line_items[0][quantity]': itemQuantity,
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
    var itemQuantity = args[8] || null;
    var paymentLink = null;

    if(paymentSuccessUrl === null) {
        error("Please specify payment success URL");
    }

    if(mode !== 'subscription') {
        // payment mode required create test product
        // createProduct
        var product = await createProduct(secretKey, 'PayProduct');
        if(product.productId === null) {
            error(product.message);
        }

        // createPrice
        var price = await createPrice(secretKey, currency, amount, product.productId);
        if(price.priceId === null) {
            error(price.message);
        }

        // createPaymentLink
        payment = await createPaymentLink(secretKey, price.priceId, paymentSuccessUrl, paymentCancelUrl);
    } else {
        payment  = await createSubscriptionLink(secretKey, priceId, customerID, itemQuantity, paymentSuccessUrl, paymentCancelUrl);
    }


    if(payment.paymentLink === null) {
        error(payment.message);
    } else {
        success(payment.paymentLink);
    }
}

require('cordova/exec/proxy').add('CordovaStripe', browserMethod);