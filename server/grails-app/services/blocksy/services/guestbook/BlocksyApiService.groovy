/**
 * The MIT License
 *
 * Copyright (c) 2017-2018 Symag. http://www.symag.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author BlockSY team - blocksy@symag.com
 */

package blocksy.services.guestbook

import blocksy.app.guestbook.User
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.util.Environment
import groovy.json.JsonBuilder
import org.springframework.http.HttpStatus
import org.springframework.util.StopWatch
import org.springframework.web.client.ResourceAccessException

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service needed to call Blocksy API
 */
class BlocksyApiService {
    private static final def FAILED_TO_CALL_BLOCKSY_API = [error: "GENERAL_ERROR"]
    private static final def ERROR_FORM_EMAIL_EXISTS = [error: "ERROR_FORM_EMAIL_EXISTS"]
    private static final def ERROR_APPLICATION = [error: "ERROR_APPLICATION"]
    private static final def ERROR_FORM_PIN = [error: "ERROR_FORM_PIN"]

    GrailsApplication grailsApplication
    SettingsService settingsService

    RestBuilder rest = new RestBuilder()
    private String blocksyRootUrl

    /**
     * Return root url of blocksy API
     * @return
     */
    def getRootUrl() {
        if (!blocksyRootUrl) {
            // Build base path for BlockSY API URL
            def blocksyProtocol = grailsApplication.config.getProperty('appconfig.blocksy_api.protocol', String)
            def blocksyIp = grailsApplication.config.getProperty('appconfig.blocksy_api.IP', String)
            def blocksyPort = grailsApplication.config.getProperty('appconfig.blocksy_api.port', String)
            def blocksyVersion = grailsApplication.config.getProperty('appconfig.blocksy_api.version', String)

            blocksyRootUrl = "${blocksyProtocol}://${blocksyIp}:${blocksyPort}/api/${blocksyVersion}/"
            log.info "BlockSY API URL root: ${blocksyRootUrl}"
        }

        blocksyRootUrl
    }

    /**
     * Create Buyer Account (blockSY call)
     * @param newUser
     * @return A map with buyer_pub_key and buyer_enc_priv_key of created buyer
     */
    def callBuyerAccount(User newUser) {
        def result = ERROR_APPLICATION

        def url = "${rootUrl}payment/buyerAccount"

        def reqBody = callBuyerAccountBuildRequest(newUser)

        RestResponse restResponse
        try {
            if (reqBody) {
                log.debug("started payment/buyerAccount, user ${newUser.name}, at ${Instant.now()}")
                StopWatch stopWatch = new StopWatch()
                stopWatch.start()
                restResponse = rest.post(url) { body(reqBody as JSON) accept "application/json" }
                stopWatch.stop()
                log.debug("ended payment/buyerAccount, user ${newUser.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")
            }
        } catch (Exception ex) {
            log.error("callBuyerAccount, url=${url}, body=${reqBody}, ex: ${ex.message}",ex)
        }

        if (restResponse) {
            if (restResponse.statusCode == HttpStatus.CREATED && restResponse.json ) {
                result = [buyer_pub_key: restResponse.json.entity.pub_key,
                          buyer_enc_priv_key: restResponse.json.encrypted_priv_key]
            } else if (restResponse.statusCode == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.error("callBuyerAccount, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else if (restResponse.statusCode == HttpStatus.BAD_REQUEST) {
                if (restResponse.json.error == "entity_name_not_unique") {
                    log.warn("callBuyerAccount, url=${url}, warn: ${restResponse.json.message}")
                    result = ERROR_FORM_EMAIL_EXISTS
                } else if (restResponse.json.error == "pin_code_invalid_value") {
                    log.warn("callBuyerAccount, url=${url}, warn: ${restResponse.json.message}")
                    result = ERROR_FORM_PIN
                } else  {
                    log.error("callBuyerAccount, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                    result = ERROR_APPLICATION
                }
            } else {
                result = ERROR_APPLICATION
                log.error("callBuyerAccount, url=${url}, body=${reqBody}, error: unexpected status code ${restResponse.statusCode}")
            }
        } else {
            result = FAILED_TO_CALL_BLOCKSY_API
        }

        result
    }

    private def callBuyerAccountBuildRequest(User newUser) {
        def reqBody = null

        try {
            def nodeConfig = settingsService.getBlocksyNodeConfig(newUser.crypto_currency)

            def ownerPubKey = nodeConfig.owner_pubkey
            def nodeTypeId = nodeConfig.type_uid
            def allowDuplicateUser = grailsApplication.config.getProperty('appconfig.allow_duplicate_user',Boolean, false)

            def otherInfoBuilder = new JsonBuilder([name: newUser.name,
                                                    email_address: newUser.email,
                                                    company: newUser.company ?: "",
                                                    twitter_id: newUser.twitter_id ?: "",
                                                    twitter_screenName: newUser.twitter_screenName ?: "",
                                                    twitter_accesstoken: newUser.twitter_accessToken ?: "",
                                                    photo_url: newUser.photo_url ?: ""
                                                   ])

            def uniqueId =  newUser.email
            if (allowDuplicateUser) {
                uniqueId += " " + UUID.randomUUID().toString()
            }

            reqBody = [
                    created_by    : ownerPubKey,
                    parent_pub_key: ownerPubKey,
                    name          : uniqueId,
                    spec_version  : 1,
                    client_id     : newUser.email,
                    type_uid      : nodeTypeId,
                    type_version  : 1,
                    status        : 1,
                    other_info    : otherInfoBuilder.toString(),
                    no_password   : false,
                    pin_code      : newUser.pin
            ]
        } catch (Exception ex)
        {
            log.error("callBuyerAccountBuildRequest, ex: ${ex.message}",ex)
        }

        reqBody
    }

/**
 * Create an order (blockSY call)
 * @param buyerPubKey
 * @param buyerUser
 * @return A Map with transaction_uid, qr_code
 */
    def callPaymentOrder(String buyerPubKey, User buyerUser) {
        def result = ERROR_APPLICATION
        def url = "${rootUrl}payment/paymentOrder"

        def reqBody = callPaymentOrderBuildRequest(buyerPubKey,buyerUser)

        RestResponse restResponse

        try {
            if (reqBody) {
                log.debug("started payment/paymentOrder, user ${buyerUser.name}, at ${Instant.now()}")
                StopWatch stopWatch = new StopWatch()
                stopWatch.start()
                restResponse = rest.post(url) { body(reqBody as JSON) accept "application/json" }
                stopWatch.stop()
                log.debug("ended payment/paymentOrder, user ${buyerUser.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")
            }
        } catch (Exception ex) {
            log.error("callPaymentOrder, url=${url}, body=${reqBody}, ex: ${ex.message}",ex)
        }

        if (restResponse) {
            if (restResponse.statusCode == HttpStatus.CREATED && restResponse.json) {
                result = [transaction_uid: restResponse.json.uid,
                          qr_code        : restResponse.json.payment_order.qr_code]
            } else if (restResponse.statusCode == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.error("callPaymentOrder, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else if (restResponse.statusCode == HttpStatus.BAD_REQUEST) {
                log.error("callPaymentOrder, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else {
                result = ERROR_APPLICATION
                log.error("callPaymentOrder, url=${url}, body=${reqBody}, error: unexpected status code ${restResponse.statusCode}")
            }
        } else {
            result = FAILED_TO_CALL_BLOCKSY_API
        }

        result
    }

    private def callPaymentOrderBuildRequest(String buyerPubKey,User buyerUser) {


        def reqBody = null

        try {
            def nodeConfig = settingsService.getBlocksyNodeConfig(buyerUser.crypto_currency)

            def fromPubKey = nodeConfig.owner_pubkey
            Integer amountForUser = grailsApplication.config.getProperty('appconfig.investment.amount_per_user', Integer, 0)
            String  currencyForUser = grailsApplication.config.getProperty('appconfig.investment.currency',String, "")
            Long amountForTransaction = nodeConfig.transaction_min_amount //minimal needed currency for a transaction
            String  currencyForTransaction = nodeConfig.transaction_min_amount_currency // iso currency code

            def otherInfoBuilder = new JsonBuilder([message: buyerUser.message,
                                                    user_amount: amountForUser,
                                                    user_currency_code: currencyForUser,
                                                    ])

            reqBody = [from_pub_key: fromPubKey, to_pub_key: buyerPubKey,
                       amount: amountForTransaction,
                       currency_code: currencyForTransaction,
                       operation: "credit user",
                       other_info: otherInfoBuilder.toString()
                      ]
        } catch (Exception ex) {
            log.error("callPaymentOrderBuildRequest, ex: ${ex.message}",ex)
        }
        reqBody
    }

def callPaymentOrderForOperation(String encPivKey, String fromPubKey, String toPubKey, String operation, String userName, String userEmail, String userLang, String userTwitterId, String ownerMultichainId) {
    RestResponse restResponse
    def result = ERROR_APPLICATION
    def url = "${rootUrl}payment/paymentOrder"
    def reqBody = callPaymentOrderForOperationBuildRequest(encPivKey,fromPubKey, toPubKey, operation, userName, userEmail, userLang, userTwitterId, ownerMultichainId)

    try {
        if (reqBody) {
            restResponse = rest.post(url) { body(reqBody as JSON) accept "application/json" }
        }
    } catch (Exception ex) {
        log.error("callPaymentOrderForOperation, url=${url}, body=${reqBody}, ex: ${ex.message}",ex)
    }

    if (restResponse) {
        if (restResponse.statusCode == HttpStatus.CREATED && restResponse.json) {
            result = [transaction_uid: restResponse.json.uid,
                      redirect_url   : restResponse.json.payment_order.redirect_url
            ]
        } else if (restResponse.statusCode == HttpStatus.UNPROCESSABLE_ENTITY) {
            log.error("callPaymentOrderForOperation, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
            result = ERROR_APPLICATION
        } else if (restResponse.statusCode == HttpStatus.BAD_REQUEST) {
            log.error("callPaymentOrderForOperation, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
            result = ERROR_APPLICATION
        } else {
            result = ERROR_APPLICATION
            log.error("callPaymentOrderForOperation, url=${url}, body=${reqBody}, error: unexpected status code ${restResponse.statusCode}")
        }
    } else {
        result = FAILED_TO_CALL_BLOCKSY_API
    }

    result
}

    private def callPaymentOrderForOperationBuildRequest(String encPivKey, String fromPubKey, String toPubKey, String operation, String userName, String userEmail, String userLang, String userTwitterId, String ownerMultichainId) {
        def reqBody = null

        def userAmount = grailsApplication.config.getProperty('appconfig.investment.amount_per_user', Integer, 0)
        def userCurrency = grailsApplication.config.getProperty('appconfig.investment.currency',String)

        try {
            def otherInfoBuilder = new JsonBuilder([operation: operation,
                                                    user_name: userName,
                                                    user_email: userEmail,
                                                    twitter_id: userTwitterId,
                                                    user_amount: userAmount,
                                                    user_currency_code: userCurrency,
                                                    owner_multichain_id: ownerMultichainId
                                                    ])

            reqBody = [
                    from_pub_key: fromPubKey, to_pub_key: toPubKey,
                    encrypted_priv_key: encPivKey,
                    amount: userAmount,
                    currency_code: userCurrency,
                    operation: operation,
                    display_lang: userLang,
                    other_info: otherInfoBuilder.toString()
            ]
        } catch (Exception ex) {
            log.error("callPaymentOrderForOperationBuildRequest, ex: ${ex.message}",ex)
        }

        reqBody
    }

/**
 * Create effective transaction using order reference (blockSY call)
 * @param paymentOrderId
 * @return A Map with transaction_uid, amount
 */
    def callCreditBuyerAccount(String transactionId, Map followMap) {
        RestResponse restResponse
        def result = ERROR_APPLICATION
        def url = "${rootUrl}payment"

        def reqBody = callCreditBuyerAccountBuildRequest(transactionId, followMap.user.crypto_currency)

        try {
            if (reqBody) {
                log.debug("started payment, user ${followMap.user.name}, at ${Instant.now()}")
                StopWatch stopWatch = new StopWatch()
                stopWatch.start()
                restResponse = rest.post(url) { body(reqBody as JSON) accept "application/json" }
                stopWatch.stop()
                log.debug("ended payment, user ${followMap.user.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")

            }
        } catch (Exception ex) {
            log.error("callCreditBuyerAccount, url=${url}, body=${reqBody}, ex: ${ex.message}",ex)
        }

        if (restResponse) {
            if (restResponse.statusCode == HttpStatus.OK && restResponse.json) {
                result = callCreditBuyerAccountBuildResponse(restResponse)
            } else if (restResponse.statusCode == HttpStatus.UNPROCESSABLE_ENTITY) {
                log.error("callCreditBuyerAccount, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else if (restResponse.statusCode == HttpStatus.BAD_REQUEST) {
                log.error("callCreditBuyerAccount, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else if (restResponse.statusCode == HttpStatus.FORBIDDEN) {
                log.error("callCreditBuyerAccount, url=${url}, body=${reqBody}, error: ${restResponse.json.message}")
                result = ERROR_APPLICATION
            } else {
                result = ERROR_APPLICATION
                log.error("callCreditBuyerAccount, url=${url}, body=${reqBody}, error: unexpected status code ${restResponse.statusCode}")
            }
        } else {
            result = FAILED_TO_CALL_BLOCKSY_API
        }

        result
    }

    private def callCreditBuyerAccountBuildRequest(String transactionId, String cryptoCurrency) {

        def reqBody = null

        try {
            def nodeConfig = settingsService.getBlocksyNodeConfig(cryptoCurrency)
            def privKey = nodeConfig.owner_privkey

            reqBody = [priv_key: privKey,
                           transaction_uid: transactionId
                      ]
        } catch (Exception ex) {
            log.error("callCreditBuyerAccountBuildRequest, ex: ${ex.message}",ex)
        }

        reqBody
    }

    private callCreditBuyerAccountBuildResponse(RestResponse restResponse) {
        def result = ERROR_APPLICATION
        String transaction_hash = null
        try {
            if (restResponse.json.blockchain_ids)
                transaction_hash = restResponse.json.blockchain_ids[0].id

            result = [transaction_uid : restResponse.json.uid,
                      amount          : restResponse.json.amount,
                      transaction_hash: transaction_hash
                     ]
        } catch (Exception ex) {
            log.error("callCreditBuyerAccountBuildResponse, ex: ${ex.message}",ex)
        }

        result
    }

    /**
     * Get information about a blocksy identity
     * @param pub_key
     * @return Json array of entities
     */
    def callFindEntities(String pub_key) {
        def result = null

        def url = "${rootUrl}identity/findEntities?pub_key=${pub_key}"

        RestResponse restResponse = rest.get(url) {
            accept "application/json"
        }

        if ( restResponse.statusCode == HttpStatus.OK && restResponse.json ) {
            result = restResponse.json
        }
        result
    }


    /**
     * Get information about a blocksy transaction
     * @param transaction_uid
     * @return Json transaction
     */
    def callTransactions(String transaction_uid) {
        def result = null

        def url = "${rootUrl}transaction/${transaction_uid}"
        try {
            RestResponse restResponse = rest.get(url) {
                accept "application/json"
            }

            if ( restResponse.statusCode == HttpStatus.OK && restResponse.json ) {
                result = restResponse.json
            }
        } catch (ResourceAccessException ex)
        {
            //ignore connection problem to avoid filling logs
        }
        result
    }

    /**
     * Get information about a list of blocksy transactions found by multichain id
     * @param multichainId
     * @param page
     * @param pageSize
     * @param onlyFrom
     * @param onlyTo
     * @return Json transaction list
     */
    private static Instant lastConnectExceptionTime = Instant.now()
    def callTransactionFindByMultichainId(String multichainId, Integer page, Integer pageSize, Boolean onlyFrom, Boolean onlyTo, Boolean onlyExecuted) {
        def result = ERROR_APPLICATION
        def url = "${rootUrl}transaction/findByMultichainId?multichain_id=${multichainId}"
        if (onlyFrom) {
            url = "${url}&only_from=true"
        }
        if (onlyTo) {
            url = "${url}&only_to=true"
        }
        if (page) {
            url = "${url}&page=${page}"
        }
        if (pageSize) {
            url = "${url}&page_size=${pageSize}"
        }
        if (!onlyExecuted) {
            url = "${url}&status=all"
        }

        RestResponse restResponse

        try {
            restResponse = rest.get(url) { accept "application/json" }
        } catch (ConnectException cex) {
            Instant current = Instant.now()
            if (lastConnectExceptionTime < current.minus(1, ChronoUnit.MINUTES)) {
                log.error("callTransactionFindByMultichainId, url=${url}, ex: ${cex.message}",cex)
                lastConnectExceptionTime = current
            }
        } catch (Exception ex) {
            log.error("callTransactionFindByMultichainId, url=${url}, ex: ${ex.message}",ex)
        }

        if (restResponse) {
            if (restResponse.statusCode == HttpStatus.OK) {
                result = callTransactionFindByMultichainIdProcessResult(restResponse)
            } else if (restResponse.statusCode == HttpStatus.BAD_REQUEST) {
                //TODO: distinguish errors from blocksy
                result = restResponse.json
                log.error("callTransactionFindByMultichainId, url=${url}, error: ${result.error}")
            } else {
                result = ERROR_APPLICATION
                log.error("callTransactionFindByMultichainId, url=${url}, error: unexpected status code ${restResponse.statusCode}")
            }
        } else {
            result = FAILED_TO_CALL_BLOCKSY_API
        }

        result
    }

    private callTransactionFindByMultichainIdProcessResult(RestResponse restResponse) {
        def result = null
        def startTime = grailsApplication.config.getProperty('appconfig.event_start_datetime', Integer, 0)

        //if configured, remove transactions older than specified EPOCH time
        if (startTime > 0) {
            result = []
            try {
                if (restResponse.json) {
                    restResponse.json.each { trans ->
                        if (trans.created >= startTime) {
                            result.add(trans)
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("callTransactionFindByMultichainIdProcessResult, ex: ${ex.message}",ex)
            }
        } else {
            result = restResponse.json
        }

        result
    }

}

