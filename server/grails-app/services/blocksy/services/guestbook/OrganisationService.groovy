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

import blocksy.app.guestbook.PaymentRequest
import grails.core.GrailsApplication

/**
 * Service to manage organisation
 */
class OrganisationService {
    private  final static def UNKNOWN_ERROR = [error: "UNKNOWN_ERROR"]
    GrailsApplication grailsApplication
    BlocksyApiService blocksyApiService
    TransactionsOrganisationFollowerService transactionsOrganisationFollowerService

/**
 * Create a transaction payment order
 * Add transaction to follower service waiting for validation before sending user an email to confirm organisation credit
 * @param paymentRequest
 * @return url to redirect user to pin code validation page
 */
    def pay(PaymentRequest paymentRequest) {
        def resp = null
        def orderMap = blocksyApiService.callPaymentOrderForOperation(paymentRequest.user_priv_key, paymentRequest.user_pub_key, paymentRequest.association_pub_key, paymentRequest.operation, paymentRequest.user_name, paymentRequest.user_email, paymentRequest.user_lang, paymentRequest.twitter_id, paymentRequest.owner_multichain_id)
        if (orderMap instanceof Map && orderMap.error) {
            //failed to create order
            resp = orderMap
        } else {
            def followMap = [transaction_uid: orderMap.transaction_uid, user_priv_key: paymentRequest.user_priv_key, user_pub_key: paymentRequest.user_pub_key, organisation_pub_key: paymentRequest.association_pub_key, user_name: paymentRequest.user_name, user_email: paymentRequest.user_email, user_lang: paymentRequest.user_lang, user_crypto_currency: paymentRequest.crypto_currency]
            transactionsOrganisationFollowerService.addFirst(followMap)
            def externalUrl = grailsApplication.config.getProperty('appconfig.external_url',"please_configure_external_url")
            resp = "${externalUrl}/blocksy${orderMap.redirect_url}"
        }

        resp
    }

    /**
     * Find an Organisation by it's multichain id. Organisations are declared in application yml configuration file
     * @param organisationMultichainId
     * @return organisation
     */
    def getByMultichainId(String organisationMultichainId) {
        def result = null
        try {
            grailsApplication.config.appconfig.organisations.each { orga ->
                if (orga.multichain_id == organisationMultichainId) {

                    //remove privkey
                    orga.blocksy_nodes.each { node ->
                        node.value.remove('privkey')
                    }

                    result = orga
                    return true
                }
            }
        } catch (Exception ex) {
            log.error("getByMultichainId, ex: ${ex.message}",ex)
        }
        result
    }

    /**
     * Find an Organisation by it's pubkey. Organisations are declared in application yml configuration file
     * @param organisationPubKey
     * @return organisation
     */
    def getByPubKey(String organisationPubKey) {
        def result = null
        try {
            grailsApplication.config.appconfig.organisations.each { orga ->

                orga.blocksy_nodes.each { node ->
                    if (node.value.pubkey == organisationPubKey) {

                        //remove privkey
                        orga.blocksy_nodes.each { node2 ->
                            node2.value.remove('privkey')
                        }

                        result = orga
                        return true
                    }
                }
            }
        } catch (Exception ex) {
            log.error("getByPubKey, ex: ${ex.message}",ex)
        }
        result
    }

/**
 * Get a paged list of transactions for an organisation
 * @param association_multichain_id
 * @param page
 * @param pageSize
 */
    def getOrganisationTransactions(String association_multichain_id, Integer page, Integer pageSize, Boolean clearSensitiveData) {
        def result = UNKNOWN_ERROR

        def orga = getByMultichainId(association_multichain_id)
        if (orga) {
            def transactionsMap = blocksyApiService.callTransactionFindByMultichainId(orga.multichain_id, page, pageSize, false, true, true)

            if (transactionsMap instanceof Map && transactionsMap.error) {
                //failed to find transactions
                result = transactionsMap
            } else {
                def filteredResult = [:]
                if (!transactionsMap.isEmpty()) {
                    filteredResult = getOrganisationTransactionsParseResult(transactionsMap, clearSensitiveData)
                }
                result = [entity: orga, transactions: filteredResult]
            }
        } else {
            result = [entity: [:], transactions: [:]]
        }
        result
    }

    private def getOrganisationTransactionsParseResult(def transactionsMap, Boolean clearSensitiveData) {
        def configuredOwerMultichainId = grailsApplication.config.appconfig.owner.multichain_id
        def filteredResult = [:]
        try {
            filteredResult = transactionsMap.findAll { t ->
                if (t.payment_order?.other_info) {
                    t.payment_order.other_info.owner_multichain_id == configuredOwerMultichainId
                } else
                    false
            }
            if (clearSensitiveData)
                removeSensitiveTransactionDataAndSetDonationAmount(filteredResult)
        } catch (Exception ex) {
            log.error("getOrganisationTransactionsParseResult, ex: ${ex.message}",ex)
        }
        filteredResult
    }

    /**
     * Remove sensitive data in a transaction and set donation amount
     * @param data
     */
    public void removeSensitiveTransactionDataAndSetDonationAmount(def data) {
        data.each { elem ->
            if (elem.payment_order) {
                elem.amount = elem.payment_order.amount
                elem.currency_code = elem.payment_order.currency_code
                elem.remove("payment_order")
            }
            if (elem.other_info) {
                elem.remove("other_info")
            }
        }
    }

/**
 * Get a paged list of balance for an organisation
 * @param association_multichain_id
 * @param page
 * @param pageSize
 */
    def getOrganisationBalance(String association_multichain_id, Integer page, Integer pageSize) {
        def result = UNKNOWN_ERROR
        def orga = getByMultichainId(association_multichain_id)

        def transactionsMap = blocksyApiService.callTransactionFindByMultichainId(orga.multichain_id, page, pageSize, false, true, true)

        if (transactionsMap instanceof Map && transactionsMap.error) {
            //failed to find transactions
            result = transactionsMap
        } else {
            def amountMax = grailsApplication.config.getProperty('appconfig.donation.amount_max', Integer, 0)
            result = [entity: orga] + getOrganisationBalanceParseResponse(transactionsMap) + [max: amountMax]
        }
        result
    }

    /**
     * Get a paged list of balance for all organisations
     * @param page
     * @param pageSize
     */
    def getOrganisationsBalance( Integer page, Integer pageSize) {
        def results = []
        def result
        grailsApplication.config.appconfig.organisations.each {
            def subResult = getOrganisationBalance(it.multichain_id, page, pageSize)
            if (! (subResult instanceof Map && subResult.error))
                results.add(subResult)
        }
        result = [donationsBalance: results]
        result
    }

    private def getOrganisationBalanceParseResponse(def transactionsMap) {
        def result = [balance: 0, currency: ""]
        def configuredOwerMultichainId = grailsApplication.config.appconfig.owner.multichain_id
        try {
            transactionsMap.each { elem ->
                if (elem.payment_order && elem.payment_order.currency_code && elem.payment_order.amount && elem.payment_order.other_info) {
                    // Initialize currency information
                    if (!result.currency)
                        result.currency = elem.payment_order.currency_code

                    if (result.currency == elem.payment_order.currency_code &&
                            elem.payment_order.other_info.owner_multichain_id == configuredOwerMultichainId) {// be sure we don't mix currencies
                        result.balance += elem.payment_order.amount
                    }
                }
            }
        } catch (Exception ex) {
            log.error("getOrganisationBalanceParseResponse, ex: ${ex.message}",ex)
        }
        result
    }

}
