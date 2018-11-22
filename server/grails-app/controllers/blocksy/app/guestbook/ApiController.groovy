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

package blocksy.app.guestbook

import blocksy.services.guestbook.OrganisationService
import blocksy.services.guestbook.SettingsService
import blocksy.services.guestbook.UserService
import grails.converters.JSON
import grails.core.GrailsApplication
import grails.validation.Validateable
import net.logstash.logback.marker.Markers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.springframework.http.HttpStatus

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ApiController {
    private  final static def ERROR_APPLICATION = "ERROR_APPLICATION"
    SettingsService settingsService
    UserService userService
    OrganisationService organisationService
    GrailsApplication grailsApplication
    def messageSource
    final Marker functionalMarker = Markers.appendEntries([category: "functional"]);

    /**
     * GET /api/settings retrieve server settings
     * @return Settings
     */
    def settings() {
        try{ log.warn(functionalMarker, "GET /api/settings : Get client settings") } catch (Exception) {}
        render(contentType: "application/json", encoding: "UTF-8", settingsService.getClientSettings() as JSON)
    }

    /**
     * GET /api/messages list guestbook messages
     * @return List<UserMessage>
     */
    def listMessages(MessagePage messagePage) {
        try{ log.warn(functionalMarker, "GET /api/messages : List guestbook messages. Request params: pages=${messagePage.pages}, pageSize=${messagePage.pageSize}") } catch (Exception) {}
        def messageList = userService.getAllMessages(messagePage)
        if (messageList == null){
            render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
        }else {
            render(messageList as JSON, status: HttpStatus.OK, contentType: 'application/json')
        }
    }

    /**
     * POST /api/users creates a user in blocksy with a HD Wallet
     * @param newUser is User you want to create.
     * @return blocksy created user's public and private keys
     */
    def createUser(User newUser){
        try{ log.warn(functionalMarker, "POST /api/users : Create user '${newUser.name}', publish his message, credit his account. Request body: ${newUser.toString()}") } catch (Exception) {}
        if (newUser.hasErrors()) {
            respond newUser.errors
        } else {
            def userKeys = userService.create(newUser)
            if (userKeys == null) {
                render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
            } else {
                if (userKeys instanceof Map && userKeys.error) {
                    // TODO: shouldn't we use error instead of status?
                    render(text: userKeys.status, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
                } else {
                    render(userKeys as JSON, status: HttpStatus.CREATED, contentType: 'application/json')
                }
            }
        }
    }

    /**
     * GET api/donate call blocky paymentOrder
     * @param paymentRequest model
     * @return do a redirection to blocksy's provided payment authorisation url
     */
    def sendPayment(PaymentRequest paymentRequest) {
        if (paymentRequest.hasErrors()) {
            respond paymentRequest.errors
        } else {
            def orga = organisationService.getByPubKey(paymentRequest.association_pub_key)
            try{ log.warn(functionalMarker, "GET /api/donate : Send user '${paymentRequest.user_name}' donation to organisation '${orga?.name[paymentRequest.user_lang]}'. Request body: ${paymentRequest.toString()}") } catch (Exception) {}

            def redirUrl = organisationService.pay(paymentRequest)
            if (redirUrl == null) {
                render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
            } else {
                if (redirUrl instanceof Map && redirUrl.error) {
                    // TODO: shouldn't we use error instead of status?
                    render(text: redirUrl.status, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
                } else {
                    redirect(url: redirUrl)
                }
            }
        }
    }

    /**
     * GET /api/donations/association_multichain_id/balance
     * @param id of association
     * @return List<AssociationBalance>
     */
    def associationBalance(String association_multichain_id) {
        def orga = organisationService.getByMultichainId(association_multichain_id)
        def defLocale = grailsApplication.config.appconfig.default_locale
        try{ log.warn(functionalMarker, "GET /api/donations/${association_multichain_id}/balance : Retrieve organisation '${orga?.name[defLocale]}' balance. Request params: association_multichain_id=${association_multichain_id}") } catch (Exception) {}
        def result = organisationService.getOrganisationBalance(association_multichain_id, 1, 999999)
        if (result == null){
            render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
        }else {
            if (result instanceof Map && result.error) {
                // TODO: shouldn't we use error instead of status?
                render(text: result.status, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
            } else {
                render(result as JSON, status: HttpStatus.OK, contentType: 'application/json')
            }
        }
    }

    /**
     * GET /api/donations/balance
     * @return List<AssociationsBalance>
     */
    def associationsBalance() {
        try{ log.warn(functionalMarker, "GET /api/donations/balance : Retrieve all organisations balance.") } catch (Exception) {}
        def result = organisationService.getOrganisationsBalance( 1, 999999)
        if (result == null){
            render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
        }else {
            if (result instanceof Map && result.error) {
                // TODO: shouldn't we use error instead of status?
                render(text: result.status, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
            } else {
                render(result as JSON, status: HttpStatus.OK, contentType: 'application/json')
            }
        }
    }

    /**
     * GET /api/donations/association_multichain_id
     * @param pub_key of association
     * @return List<Association,Transactions>
     */
    def associationTransactions(String association_multichain_id) {
        def orga = organisationService.getByMultichainId(association_multichain_id)
        def defLocale = grailsApplication.config.appconfig.default_locale
        try{ log.warn(functionalMarker, "GET /api/donations/${association_multichain_id} : Retrieve transactions targeting organisation '${orga?.name[defLocale]}'. Request params: association_multichain_id=${association_multichain_id}") } catch (Exception) {}
        def result = organisationService.getOrganisationTransactions(association_multichain_id, 1, 999999, true)
        if (result == null){
            render(text: ERROR_APPLICATION, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
        }else {
            if (result instanceof Map && result.error) {
                // TODO: shouldn't we use error instead of status?
                render(text: result.status, status: HttpStatus.FORBIDDEN, contentType: 'text/plain')
            } else {
                render(result as JSON, status: HttpStatus.OK, contentType: 'application/json')
            }
        }
    }

    /**
     * GET /api/donations
     * @return List<Associations,Transactions>
     */
    def associationsTransactions() {
        try{ log.warn(functionalMarker, "GET /api/donations : Retrieve transactions for all organisations.") } catch (Exception) {}
        def entitiesList = []
        def ownerPrivKey = grailsApplication.config.appconfig.owner.blocksy_privkey
        def isCsvExport = (params.output && params.output == "csv" && params.priv_key && params.priv_key == ownerPrivKey)
        def parseList = grailsApplication.config.appconfig.organisations

        parseList.each {orga ->
            def entityTransactionsList=organisationService.getOrganisationTransactions(orga.multichain_id,1, 999999, !isCsvExport)
            entitiesList.add(entityTransactionsList)
        }

        if (isCsvExport) {
            def result = getAsCsvForMicroDon(entitiesList)
            response.setHeader "Content-disposition", "attachment; filename=${getExportFileName()}"
            render(text: result, status: HttpStatus.OK, contentType: 'text/csv')
        } else {
            def result = [entities: entitiesList]
            render(result as JSON, status: HttpStatus.OK, contentType: 'application/json')
        }
    }

    private def getExportFileName() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyMMdd")
        def startTime = grailsApplication.config.appconfig.event_start_datetime
        LocalDateTime startDate = LocalDateTime.ofEpochSecond(startTime, 0, ZoneOffset.UTC)
        def startDateString = startDate.format(formatter)
        def endDateString = LocalDateTime.now().format(formatter)
        def filename = "BLOCKSY-${startDateString}-${endDateString}-dons.csv"
        filename
    }

    private String getAsCsvForMicroDon(def entitiesList) {
        def result = ""
        entitiesList.each { entry ->
            def microdonId = entry.entity.microdon_id
            entry.transactions.each { transaction ->
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(transaction.created, 0, ZoneOffset.UTC);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");
                def zdt = ZonedDateTime.of(dateTime, ZoneId.systemDefault())
                String donationDate = zdt.format(formatter);

                def userDetails = userService.getUserDetailsWithPubKey(transaction.from)
                DecimalFormat df = new DecimalFormat("0.00")
                String donationAmount = df.format(transaction.payment_order.amount)

                def line = /* collector_id */         "BLOCKSY"     + ";" +
                        /* donation_date */        donationDate     + ";" +
                        /* donation_value */        donationAmount   + ";" +
                        /* donation_payeur */      "SYMAG"  + ";" +
                        /* donation_beneficiaire */ microdonId  + ";" + //  ou ‘ PLNT_URG_75 ’
                        /* type_transac */         "TRESORERIE" + ";" +
                        /* devise */               "EUR" + ";" +
                        /* Nom_du_client */       "\"" + userDetails.user_name + "\"" + ";" +
                        /* Email_du_client */     "\"" + userDetails.user_email + "\""

                result+=line + "\n"
            }
        }
        result
    }
}
