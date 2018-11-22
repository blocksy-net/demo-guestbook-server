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

import blocksy.app.guestbook.BlockExplorerHelper
import blocksy.app.guestbook.MessagePage
import blocksy.app.guestbook.User
import blocksy.app.guestbook.UserMessage
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import grails.core.GrailsApplication
import org.grails.web.json.JSONArray
import org.springframework.util.StopWatch

import java.time.Instant
import java.util.concurrent.TimeUnit
import static grails.async.Promises.*


/**
 * Service to manage users
 */
class UserService {
    GrailsApplication grailsApplication
    MailingService mailingService
    BlocksyApiService blocksyApiService
    TransactionsUserFollowerService transactionsUserFollowerService
    TwitterService twitterService

    /**
     * Create a new User in blockSY blockchain,
     * create a blockSY wallet for user,
     * create a payment order and take into account user message,
     * credit user wallet with amount needed for event
     * send user an email to confirm account creation
     * @param newUser
     * @return map with pub_key attribute
     */
    def create(User newUser) {
        def result
        def resp = null
        // to receive Twitter auth infos
        TreeMap auth = setTwitterInfoAndAuth(newUser)

        //def buyerMap = [buyer_pub_key: "a5207f936889a8884ee31f61d073185b9d62b8a2", buyer_enc_priv_key: "73821d82bcf30f525364b31352dc41f22JBHQ28E6BQRBUQ5zmFkKqparFo6pSzCUR6kC75C3Fpw4uwt3zZ1PfGJPHDF1gjExd5VQtLUx7xcUHrkX2RSySaZaWCmzcLLotwyvEVvyBpNKV"]
        def buyerMap = blocksyApiService.callBuyerAccount(newUser)
        if (buyerMap instanceof Map && buyerMap.error) {
            //failed to create buyer account
            result = buyerMap
        } else {
            //create an order to credit user
            //def orderMap = [transaction_uid: "a913ab53-51c3-4697-86bf-7f83f7318b9f", qr_code: "bitcoin%3AmuYLUCtSmdGox7xRtXAo45adEnUrmkbNwF%3Famount%3D2730%26label%3DJean+Bon%26message%3DPurchase+at+Jean+Bon"]
            def orderMap = blocksyApiService.callPaymentOrder(buyerMap.buyer_pub_key, newUser)
            if (orderMap instanceof Map && orderMap.error) {
                //failed to create order
                result = orderMap
            } else {
                def followMap = [priv_key: buyerMap.buyer_enc_priv_key, pub_key: buyerMap.buyer_pub_key, user: newUser]
                //def creditMap = [transaction_uid: "a913ab53-51c3-4697-86bf-7f83f7318b9f", amount: "2730"]
                def creditMap = blocksyApiService.callCreditBuyerAccount(orderMap.transaction_uid, followMap)
                if (creditMap instanceof Map && creditMap.error) {
                    //failed to credit buyer
                    result = creditMap
                }
                else if (creditMap instanceof Map && creditMap.warn) {
                    //blocksy not responding, transaction is delayed, but the answer is OK
                    result = creditMap

                } else {
                    followMap.put("transaction_uid",creditMap.transaction_uid)
                    followMap.put("transaction_hash",creditMap.transaction_hash)

                    // post tweet
                    if (auth) {
                        def tweet = task {
                            log.info "tweet called in async"
                            twitterService.tweet(newUser, creditMap.transaction_hash, auth)
                        }
                    }

                    // listen to transaction status changes
                    transactionsUserFollowerService.addFirst(followMap)
                }
            }
        }

        if (result && result instanceof Map && result.error) {
            resp = result
        } else {
            mailingService.sendEmailConfirmBuyerAccount(buyerMap.buyer_pub_key, buyerMap.buyer_enc_priv_key, newUser)

            resp = [pub_key: buyerMap.buyer_pub_key]
        }

        resp
    }

    public TreeMap setTwitterInfoAndAuth(User newUser)  {
        log.debug("started setTwitterInfoAndAuth, user ${newUser.name}, at ${Instant.now()}")
        StopWatch stopWatch = new StopWatch()
        stopWatch.start()
        TreeMap auth
        if (newUser.twitter_id == null) {
            newUser.twitter_id = ""
        }
        // for backward compatibility in order to distinguish old fashion tokens
        if (newUser.twitter_screenName && newUser.twitter_screenName.length() > 50) {

            log.info "encrypted twitter auth received: " + newUser.twitter_accessToken

            // client passed twitter screen name and access token secret encrypted within screen_name JSON attribute
            // for backward compatibility as well as auth0 security reasons
            // they have been encrypted for security with twitter consumer tokens
            newUser.twitter_accessToken = newUser.twitter_screenName

            // so we extract them
            auth = twitterService.decrypt(newUser.twitter_accessToken)

            //log.info auth.access_token
            //log.info auth.access_token_secret
            //log.info auth.screen_name

            // and update user with the actual value of screen_name
            // access token and token secret will be used to tweet below
            newUser.twitter_screenName = auth.screen_name
        }
        stopWatch.stop()
        log.debug("ended setTwitterInfoAndAuth, user ${newUser.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")

        auth
    }

    //Initialize message cache
    Cache<String, JSONArray> messagesCache = CacheBuilder.newBuilder()
            .maximumSize(50)//50 cached pages of 20 lines is enough
            .expireAfterWrite(7, TimeUnit.SECONDS)
            .build()

    /**
     * Get list of guestbook messages limited by a paging system
     * @param messagePage
     * @return
     */
    def getAllMessages(MessagePage messagePage) {
        def result = null

        if (! messagePage.pages) messagePage.pages = 1 //At least retrieve 1 page
        if (! messagePage.pageSize) messagePage.pageSize = 20 //Number of items in a page
        def totalRequestedPageSize = messagePage.pages * messagePage.pageSize

        def multichainId = grailsApplication.config.getProperty('appconfig.owner.multichain_id',"")
        def keyId = "${multichainId}|${messagePage.pages}|${messagePage.pageSize}"

        if (messagesCache) {
            // check if requested page is in cache
            def existingResult = messagesCache.getIfPresent(keyId)
            if (existingResult) {
                result = [messages: existingResult]
            } else {
                //check if previous page is in cache
                def keyId2 = "${multichainId}|${messagePage.pages - 1}|${messagePage.pageSize}"
                def offsetPage = 1
                if (messagePage.pages > 1) {
                    existingResult = messagesCache.getIfPresent(keyId2)
                    if (existingResult) {
                        offsetPage = messagePage.pages
                        totalRequestedPageSize = messagePage.pageSize
                    }
                }
                def transactionsMap = blocksyApiService.callTransactionFindByMultichainId(multichainId, offsetPage, totalRequestedPageSize, true, false, true)
                if (transactionsMap instanceof Map && transactionsMap.error) {
                    //failed to get transactions
                    result = [messages: []]
                } else {
                    result = parseAndCacheNewMessages(transactionsMap, offsetPage, existingResult, keyId)
                }
            }
        }

        result
    }

    private def parseAndCacheNewMessages(def transactionsMap, def offsetPage, def existingResult, def keyId) {
        def resultChilds = []
        def result
        transactionsMap.each {
            UserMessage message = buildMessage(it)
            if (message != null) {
                resultChilds.add(message)
            }
        }
        if (offsetPage > 1 && existingResult) {
            existingResult.addAll(resultChilds)
            messagesCache.put(keyId, existingResult)
            result = [messages: existingResult]
        } else {
            messagesCache.put(keyId, resultChilds)
            result = [messages: resultChilds]
        }

        result
    }

    private def buildMessage(transaction) {
        UserMessage userMessage = null
        try {
            String transactionHash = ""
            Integer coinType
            def cryptoCurrency = ""
            if (transaction.blockchain_ids) {
                transactionHash = transaction.blockchain_ids[0].id
                coinType = transaction.blockchain_ids[0].coin_type
            }

            switch (coinType) {
                case 66060000:
                    cryptoCurrency = "RINKEBY"
                    break
                case 66145000:
                    cryptoCurrency = "tBCC"
                    break
                default:
                    cryptoCurrency = "tBCC"
            }

            String urlExplorer = BlockExplorerHelper.getUri(grailsApplication, cryptoCurrency, transactionHash);
            if (urlExplorer == "") {
                log.error("getUri: failed for cryptoCurrency ${cryptoCurrency}, check config file entry block_explorer_url.")
            }

            def pubKey = transaction.to[0]
            def entities = blocksyApiService.callFindEntities(pubKey)
            if (entities && entities[0].uid) {
                if (entities[0].other_info && transaction.payment_order?.other_info) {
                    def entityOtherInfo = entities[0].other_info

                    def user_name = entityOtherInfo.name ?: "**************" //Anonymous
                    def trxOtherInfo = transaction.payment_order.other_info
                    def message = trxOtherInfo?.message ?: ''

                    userMessage = new UserMessage(id: transaction.uid, dateTime: transaction.created,
                            author: [id: transaction.payment_order.recipient_address, name: user_name ?: "", twitter_id: entityOtherInfo.twitter_id ?: "", twitter_screenName: entityOtherInfo.twitter_screenName ?: "", photo_url: entityOtherInfo.photo_url ?: ""],
                            confirmations: transaction.confirmations, url_blockexplorer: urlExplorer,
                            value: message)
                }
            }
        } catch (Exception ex) {
            log.error(ex.message, ex)
        }
        userMessage
    }

    /**
     * Get the number of users with a published message
     * @return
     */
    def getUserCount() {
        def result = grailsApplication.config.appconfig.nb_user_max //to avoid donating too much, we initialize to max_user, so in case of crash, donation will be disabled

        try {
            def multichainId = grailsApplication.config.getProperty('appconfig.owner.multichain_id')

            def transactionsMap = blocksyApiService.callTransactionFindByMultichainId(multichainId, 1, 999999, true, false, true)
            if (! (transactionsMap instanceof Map && transactionsMap.error)) {
                result = transactionsMap.size
            }
        } catch (Exception) {

        }

        result
    }

    def getUserDetailsWithPubKey(String pubKey) {
        def result = [:]

        def entities = blocksyApiService.callFindEntities(pubKey)
        if (entities) {
                def otherInfos = entities[0].other_info
                def userName = otherInfos?.name ?: "" //Empty string if missing
                def userEmail = entities[0].client_id ?: "" //Empty string if missing
                result = [user_name: userName, user_email: userEmail]
         }
        result
    }
}

