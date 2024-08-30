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
const createProduct = async (secretKey, productName, error) => {
    var productId = null;
    var requestBody = {
        name: productName,
    };
    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }

    // Call Stripe API
    await apiCall('/products', requestBody, requestHeader, 'POST', 
        (data) => {
            // get product id
            productId = data.data.id;
        } 
    , 
        (err) => {
            error(err);
        }
    );

    return productId;
}

// Create a new product 
const createPrice = async (secretKey, currency, unitAmount, productId, error) => {
    var priceId = null;
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

    // Call Stripe API
    await apiCall('/prices', requestBody, requestHeader, 'POST', 
        (data) => {
            // get product id
            priceId = data.data.id;
        } 
    , 
        (err) => {
            error(err);
        }
    );

    return priceId;
}

// Create a new product 
const createPaymentLink = async (secretKey, priceId, mode, paymentSuccessUrl, error) => {
    var paymentLink = null;
    var requestBody = {
        'line_items[0][price]': priceId,
        'line_items[0][quantity]': 1,
        mode: 'payment',
        success_url: paymentSuccessUrl || ''
    };
    requestBody = convertObjectToEncoded(requestBody);
    var requestHeader = {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': 'Bearer ' + secretKey,
    }

    // Call Stripe API
    await apiCall('/checkout/sessions', requestBody, requestHeader, 'POST', 
        (data) => {
            // get product id
            paymentLink = data.data.url;
        } 
    , 
        (err) => {
            error(err);
        }
    );

    return paymentLink;
}

// Create new payment session
browserMethod.createPaymentSession = async (success, error, args) => {
    var secretKey = args[0];
    var mode = args[1];
    var currency = args[2];
    var amount = args[3];
    var paymentSuccessUrl = args[4];

    if(paymentSuccessUrl === null) {
        error("Please specify payment success URL");
    }

    // createProduct
    var productId = await createProduct(secretKey, 'Test Product', error);
    if(productId === null) {
        error("Failed when create product to process");
    }

    // createPrice
    var priceId = await createPrice(secretKey, currency, amount, productId, error);
    if(priceId === null) {
        error("Failed when create price to process");
    }

    // // createPaymentLink
    var paymentLink = await createPaymentLink(secretKey, priceId, mode, paymentSuccessUrl, error);

    if(paymentLink === null) {
        error("Failed when create payment link to process");
    }

    success(paymentLink);
}

require('cordova/exec/proxy').add('CordovaStripe', browserMethod);