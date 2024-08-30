import Foundation

@objc(CordovaStripe) class CordovaStripe: CDVPlugin {
    private let STRIPE_API_URL = "https://api.stripe.com/v1"
    
    @objc(createPaymentSession:)
    func createPaymentSession(command: CDVInvokedUrlCommand) {
        let secretKey = command.argument(at: 0) as? String ?? ""
        let mode = "payment"
        let currency = command.argument(at: 2) as? String ?? ""
        let amount = command.argument(at: 3) as? Int ?? 0
        let paymentSuccessUrl = command.argument(at: 4) as? String ?? ""

        guard !paymentSuccessUrl.isEmpty else {
            let errorResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Please specify payment success URL")
            self.commandDelegate.send(errorResult, callbackId: command.callbackId)
            return
        }

        DispatchQueue.global().async {
            do {
                let productId = try self.createProduct(secretKey: secretKey, productName: "Test Product")
                guard let productId = productId else {
                    self.sendError(message: "Failed to create product", callbackId: command.callbackId)
                    return
                }

                let priceId = try self.createPrice(secretKey: secretKey, currency: currency, unitAmount: amount, productId: productId)
                guard let priceId = priceId else {
                    self.sendError(message: "Failed to create price", callbackId: command.callbackId)
                    return
                }

                let paymentLink = try self.createPaymentLink(secretKey: secretKey, priceId: priceId, mode: mode, paymentSuccessUrl: paymentSuccessUrl)
                guard let paymentLink = paymentLink else {
                    self.sendError(message: "Failed to create payment link", callbackId: command.callbackId)
                    return
                }

                let successResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: paymentLink)
                self.commandDelegate.send(successResult, callbackId: command.callbackId)
            } catch let error {
                self.sendError(message: "Error: \(error.localizedDescription)", callbackId: command.callbackId)
            }
        }
    }

    private func createProduct(secretKey: String, productName: String) throws -> String? {
        let endpoint = "\(STRIPE_API_URL)/products"
        let requestBody = "name=\(productName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        
        let response = try apiCall(endpoint: endpoint, requestBody: requestBody, secretKey: secretKey, method: "POST")
        return parseJsonForId(json: response)
    }

    private func createPrice(secretKey: String, currency: String, unitAmount: Int, productId: String) throws -> String? {
        let endpoint = "\(STRIPE_API_URL)/prices"
        let requestBody = "unit_amount=\(unitAmount)&currency=\(currency.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&product=\(productId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        
        let response = try apiCall(endpoint: endpoint, requestBody: requestBody, secretKey: secretKey, method: "POST")
        return parseJsonForId(json: response)
    }

    private func createPaymentLink(secretKey: String, priceId: String, mode: String, paymentSuccessUrl: String) throws -> String? {
        let endpoint = "\(STRIPE_API_URL)/checkout/sessions"
        let requestBody = "line_items[0][price]=\(priceId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&line_items[0][quantity]=1&mode=\(mode.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&success_url=\(paymentSuccessUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        
        let response = try apiCall(endpoint: endpoint, requestBody: requestBody, secretKey: secretKey, method: "POST")
        return parseJsonForUrl(json: response)
    }

     private func apiCall(endpoint: String, requestBody: String, secretKey: String, method: String) throws -> String {
        let semaphore = DispatchSemaphore(value: 0) // Create a semaphore with initial value 0
        var responseString: String? // Declare as a variable to be able to assign a value
        var responseError: Error?
        
        var request = URLRequest(url: URL(string: endpoint)!)
        request.httpMethod = method
        request.setValue("Bearer \(secretKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = requestBody.data(using: .utf8)
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                responseError = error
            } else if let data = data, let responseStringValue = String(data: data, encoding: .utf8) {
                responseString = responseStringValue
            }
            semaphore.signal() // Signal that the task is completed
        }
        
        task.resume() // Start the network task
        
        _ = semaphore.wait(timeout: .distantFuture) // Wait for the task to complete
        
        if let error = responseError {
            throw error
        }
        
        guard let responseString = responseString else {
            throw NSError(domain: "", code: 0, userInfo: [NSLocalizedDescriptionKey: "No response data"])
        }
        
        return responseString
    }

    private func parseJsonForId(json: String) -> String? {
        if let jsonData = json.data(using: .utf8) {
            do {
                if let jsonObject = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {
                    return jsonObject["id"] as? String
                }
            } catch {
                NSLog("JSON parsing error: \(error.localizedDescription)")
            }
        }
        return nil
    }

    private func parseJsonForUrl(json: String) -> String? {
        if let jsonData = json.data(using: .utf8) {
            do {
                if let jsonObject = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {
                    return jsonObject["url"] as? String
                }
            } catch {
                NSLog("JSON parsing error: \(error.localizedDescription)")
            }
        }
        return nil
    }

    private func sendError(message: String, callbackId: String) {
        let errorResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        self.commandDelegate.send(errorResult, callbackId: callbackId)
    }
}