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

import grails.core.GrailsApplication
import groovy.transform.*

/**
 * Service to keep in memory (singleton) transactions waiting for validation by blockchain peers
 */
class TransactionsUserFollowerService {
    GrailsApplication grailsApplication
    MailingService mailingService
    BlocksyApiService blocksyApiService
    UserService userService

    LinkedList transactionsWaitingForValidation = [] as LinkedList

    /**
     * Add a transaction to list as 1st element
     * @param transaction details map
     */
    @Synchronized
    void addFirst(Map transactionDetails){
        transactionDetails.put("retry_count", 0)
        transactionsWaitingForValidation.addFirst(transactionDetails)
        log.info "Added user transaction to follow list. uid: ${transactionDetails.transaction_uid}, hash: ${transactionDetails.transaction_hash}. ${logEntryMessage()}"
    }

    /**
     * Add a transaction to list as last element
     * @param transaction details map
     */
    @Synchronized
    void addLast(Map transactionDetails){
        transactionsWaitingForValidation.addLast(transactionDetails)
        log.info "Appended user transaction to follow list. uid: ${transactionDetails.transaction_uid}, hash: ${transactionDetails.transaction_hash}. ${logEntryMessage()}"
    }

    /**
     * Poll a transaction
     */
    @Synchronized
    Map pollTransaction() {
        //retrieve and remove 1st element
        Map elem = transactionsWaitingForValidation.poll()
        if (elem != null)
            log.info "Removed user transaction from follow list. uid: ${elem.transaction_uid}, transactionHash: ${elem.transaction_hash}. ${logEntryMessage()}"


        elem
    }

    private String logEntryMessage() {
        return "Follow list contains : ${transactionsWaitingForValidation.size()} user transactions."
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
                    log.error "User transaction email failed 3 times. uid: ${transaction.transaction_uid}, transactionHash: ${transaction.transaction_hash}"
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
                //retrieve blockchain's transaction hash
                def String transactionHash = null
                if (transaction.blockchain_ids)
                {
                    transactionHash = transaction.blockchain_ids[0].id
                }

                if (transactionHash) {
                    Boolean isDonationActive = checkIfDonationAllowed()

                    //send confirm email to user
                    success = mailingService.sendEmailConfirmMessageStored(transactionUid, transactionHash, transactionDetails.priv_key, transactionDetails.pub_key, transactionDetails.user, isDonationActive)
                    if (!success) {
                        transactionDetails.retry_count += 1
                    }
                }
            }
        }
        success
    }

    private def checkIfDonationAllowed() {
        Boolean isDonationActive = ( grailsApplication.config.getProperty('appconfig.donation.enabled') == 'true')
        def userAmount = grailsApplication.config.appconfig.investment.amount_per_user
        def donationLimit = grailsApplication.config.appconfig.donation.amount_max
        def donationCapacity = userAmount * userService.getUserCount()
        //If we already have the number of user needed to target the donation max amount then we forbid new donations
        if ((donationCapacity + userAmount) > donationLimit) {
            isDonationActive = false
        }
        isDonationActive
    }
}


