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

import groovy.transform.Synchronized

/**
 * Service to keep in memory (singleton) transactions waiting for validation by blockchain peers
 */
class TransactionsOrganisationFollowerService {
    MailingService mailingService
    BlocksyApiService blocksyApiService
    OrganisationService organisationService

    LinkedList transactionsWaitingForValidation = [] as LinkedList

    /**
     * Add a transaction to list at 1st position
     * @param transaction details map
     */
    @Synchronized
    void addFirst(Map transactionDetails){
        transactionDetails.put("retry_count", 0)
        transactionsWaitingForValidation.addFirst(transactionDetails)
        log.info "Added organisation transaction to follow list. uid: ${transactionDetails.transaction_uid}, user_pub_key: ${transactionDetails.user_pub_key}. ${logEntryMessage()}"
    }

    /**
     * Add a transaction to list at last position
     * @param transaction details map
     */
    @Synchronized
    void addLast(Map transactionDetails){
        transactionsWaitingForValidation.addLast(transactionDetails)
        log.info "Appended organisation transaction to follow list. uid: ${transactionDetails.transaction_uid}, user_pub_key: ${transactionDetails.user_pub_key}. ${logEntryMessage()}"
    }

    /**
     * Poll a transaction
     */
    @Synchronized
    Map pollTransaction() {
        //retrieve and remove 1st element
        Map elem = transactionsWaitingForValidation.poll()
        if (elem != null)
            log.info "Removed organisation transaction from follow list. uid: ${elem.transaction_uid}, user_pub_key: ${elem.user_pub_key}. ${logEntryMessage()}"

        elem
    }

    private String logEntryMessage() {
        return "Follow list contains : ${transactionsWaitingForValidation.size()} organisation transactions."
    }

    /**
     * Process one transaction
     */
    void processOne() {
        Map transaction = pollTransaction()
        if (transaction != null) {
            if(! lookForValidatedTransactionAndSendEmail(transaction))
            {
                if (transaction.retry_count < 4) { // allow only 3 email sending try
                    addLast(transaction)
                } else {
                    log.error "Organisation transaction email failed 3 times. uid: ${transaction.transaction_uid}, user_pub_key: ${transaction.user_pub_key}"
                }
            }
        }
    }

    private Boolean lookForValidatedTransactionAndSendEmail(Map transactionDetails)
    {
        String transactionUid=transactionDetails.transaction_uid
        def success = false
        //check if transaction is validated
        def transaction = blocksyApiService.callTransactions(transactionUid)
        if (transaction) {
            //consider transaction is valid when there's at least one confirmation
            def isValid = (transaction.confirmations > 0)
            //transaction.confidence

            if (isValid) {
                //get Organisation infos from configuration file
                def organisation = organisationService.getByPubKey(transactionDetails.organisation_pub_key)
                def organisation_name = organisation.name[transactionDetails.user_lang]

                //retrieve blockchain's transaction hash
                def String transactionHash = null
                if (transaction.blockchain_ids) {
                    transactionHash = transaction.blockchain_ids[0].id
                    transactionDetails.put("transaction_hash",transactionHash)
                }

                if (transactionHash) {
                    //send confirm email to user
                    success = mailingService.sendEmailConfirmOrganisationCredit(transactionUid, transactionHash, organisation_name, organisation.url, transactionDetails.user_name, transactionDetails.user_email, transactionDetails.user_lang, transactionDetails.user_pub_key, transactionDetails.user_priv_key, transactionDetails.user_crypto_currency)
                    if (!success) {
                        transactionDetails.retry_count += 1
                    }
                }
            }
        }
        success
    }
}