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
import blocksy.app.guestbook.User
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.plugins.mail.GrailsMailException
import groovy.text.StreamingTemplateEngine
import org.springframework.util.StopWatch

import java.time.Instant

/**
 * Service to send specific email messages to user
 */
class MailingService {
    GrailsApplication grailsApplication
    SettingsService settingsService
    def mailService
    def templateCache = [:]
    /**
     * Render an email template
     * @param templateName
     * @param lang
     * @param model
     * @return
     */
    private def renderTemplate(String templateName, String lang, Map model){
        def result = null
        try {
            def shortLang = lang.substring(0, 2).toLowerCase()
            //take only the 2 first language char. fr instead of fr_FR
            def template
            String key = "${templateName}|${shortLang}"

            if (templateCache.containsKey(key)) {
                template = templateCache.get(key)
            } else {
                File fileTemplate = new File("emails/${shortLang}/${templateName}.tmpl")
                template = new StreamingTemplateEngine().createTemplate(fileTemplate)
            }
            result = template.make(model)
        } catch(Exception ex) {
            log.error("renderTemplate ${templateName}, ${lang}, ex: ${ex.message}", ex)
        }
        result
    }
    
    /**
     * Send an email
     * @param toEmail
     * @param fromEmail
     * @param textSubject
     * @param htmlBody
     * @return
     */
    private def send(String toEmail, String textSubject, String htmlBody) {
        def result = (textSubject != null && htmlBody != null)
        String senderEmail = grailsApplication.config.getProperty('appconfig.email.sender_email',"")
        try {
            mailService.sendMail {
                to toEmail
                from senderEmail
                subject textSubject
                html htmlBody
            }
        } catch(GrailsMailException ex) {
            result = false
            log.error("send, ex: ${ex.message}",ex)
        }
       result
    }

    /**
     * Send an email to user, to confirm account created
     * @param userPubKey
     * @param encodedUserPrivKey
     * @param user
     * @return
     */
    def sendEmailConfirmBuyerAccount(String userPubKey, String encodedUserPrivKey, User user) {
        log.debug("started sendEmailConfirmBuyerAccount, user ${user.name}, at ${Instant.now()}")
        StopWatch stopWatch = new StopWatch()
        stopWatch.start()
        def result = false
        def amountCredit = grailsApplication.config.getProperty('appconfig.investment.amount_per_user')
        def currencyCredit = grailsApplication.config.getProperty('appconfig.investment.currency')
        String eventName = grailsApplication.config.getProperty('appconfig.owner.event')
        String title = grailsApplication.config.appconfig.title[user.user_lang]
        String donationDonator = grailsApplication.config.appconfig.donation.donator[user.user_lang]
        String donationRules = grailsApplication.config.appconfig.donation.rules[user.user_lang]
        String textSubject = renderTemplate("confirmWalletCreated_subject", user.user_lang, [event_name: eventName, title: title])
        String htmlBody = renderTemplate("confirmWalletCreated", user.user_lang, [pub_key: userPubKey, qr_code: encodedUserPrivKey, user: user, amount_credit: amountCredit, currency_credit: currencyCredit, donation_rules: donationRules, donation_donator: donationDonator])

        result = send(user.email, textSubject, htmlBody)
        stopWatch.stop()
        if (result) {
            log.info("Email with template 'confirmWalletCreated' successfully sent to ${user.email}")
        } else {
            log.info("Email with template 'confirmWalletCreated' failed to be sent to ${user.email}")
        }
        log.debug("ended sendEmailConfirmBuyerAccount, user ${user.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")
        result
    }

    /**
     * Send an email to user, to confirm message is stored and user account is credited
     * @param transactionHash
     * @param userPrivKey
     * @param userPubkey
     * @param user
     * @return
     */
    def sendEmailConfirmMessageStored(String transactionUid, String transactionHash,String userPrivKey, String userPubKey, User user, Boolean isDonationActive) {
        def result = false
        def amountCredit = grailsApplication.config.getProperty('appconfig.investment.amount_per_user')
        def currencyCredit = grailsApplication.config.getProperty('appconfig.investment.currency')
        def cryptoCurrency = user.crypto_currency
        def ArrayList organisations = grailsApplication.config.appconfig.organisations
        def demoServerUrl = grailsApplication.config.appconfig.external_url
        def ownerMultichainId = grailsApplication.config.appconfig.owner.multichain_id
        String donationDonator = grailsApplication.config.appconfig.donation.donator[user.user_lang]
        String donationRules = grailsApplication.config.appconfig.donation.rules[user.user_lang]
        String eventName = grailsApplication.config.getProperty('appconfig.owner.event')
        String title = grailsApplication.config.appconfig.title[user.user_lang]

        String blockExplorerUri = BlockExplorerHelper.getUri(grailsApplication, cryptoCurrency, transactionHash);
        if (blockExplorerUri == "") {
            log.error("getUri: failed for cryptoCurrency ${cryptoCurrency}, check config file entry block_explorer_url.")
        }

        String textSubject = renderTemplate("confirmMessageStored_subject", user.user_lang, [event_name: eventName, title: title ])
        String htmlBody = renderTemplate("confirmMessageStored", user.user_lang, [lang: user.user_lang, transaction_uid: transactionUid, transaction_hash: transactionHash, amount_credit: amountCredit,
                                                                               currency_credit: currencyCredit, block_explorer_href: blockExplorerUri, donation_active: isDonationActive, organisations: organisations,
                                                                               api_root_url: demoServerUrl,user_priv_key: userPrivKey, user_pub_key: userPubKey, user: user, owner_multichain_id: ownerMultichainId,
                                                                               donation_rules: donationRules, donation_donator: donationDonator])

        result = send(user.email, textSubject, htmlBody)
        if (result) {
            log.info("Email with template 'confirmMessageStored' successfully sent to ${user.email}")
        } else {
            log.info("Email with template 'confirmMessageStored' failed to be sent to ${user.email}")
        }
        result
    }

    /**
     * Send an email to user, to confirm organisation account is credited
     * @param transactionHash
     * @param organisation_name
     * @param organisation_url
     * @param user_name
     * @param user_email
     * @param user_lang
     * @return
     */
    def sendEmailConfirmOrganisationCredit(String transactionUid, String transactionHash, String organisationName, String organisationUrl, String userName, String userEmail, String userLang, String userPubKey, String userPrivKey, String cryptoCurrency) {
        def result = false
        def amountCredit = grailsApplication.config.getProperty('appconfig.investment.amount_per_user')
        def currencyCredit = grailsApplication.config.getProperty('appconfig.investment.currency')
        def demoServerUrl = grailsApplication.config.appconfig.external_url
        String donationDonator = grailsApplication.config.appconfig.donation.donator[userLang]
        String donationRules = grailsApplication.config.appconfig.donation.rules[userLang]
        String eventName = grailsApplication.config.getProperty('appconfig.owner.event')
        String title = grailsApplication.config.appconfig.title[userLang]
        String blockExplorerUri = BlockExplorerHelper.getUri(grailsApplication, cryptoCurrency, transactionHash);
        if (blockExplorerUri == "") {
            log.error("getUri: failed for cryptoCurrency ${cryptoCurrency}, check config file entry block_explorer_url.")
        }

        String textSubject = renderTemplate("confirmOrganisationCredit_subject", userLang, [event_name: eventName, title: title])
        String htmlBody = renderTemplate("confirmOrganisationCredit", userLang, [lang: userLang, transaction_hash: transactionHash, amount_credit: amountCredit,
                                                                                    currency_credit: currencyCredit, block_explorer_href: blockExplorerUri, organisation_name: organisationName,
                                                                                    organisation_url: organisationUrl, user_name: userName, api_root_url: demoServerUrl, transaction_uid: transactionUid,
                                                                                    user_pub_key: userPubKey, user_priv_key: userPrivKey, donation_rules: donationRules, donation_donator: donationDonator])

        result = send(userEmail, textSubject, htmlBody)
        if (result) {
            log.info("Email with template 'confirmOrganisationCredit' successfully sent to ${userEmail}")
        } else {
            log.info("Email with template 'confirmOrganisationCredit' failed to be sent to ${userEmail}")
        }
        result
    }

}

