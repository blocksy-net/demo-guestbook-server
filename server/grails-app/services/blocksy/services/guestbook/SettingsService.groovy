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

import blocksy.app.guestbook.MessagePage
import blocksy.app.guestbook.User
import blocksy.app.guestbook.UserMessage
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import grails.converters.JSON
import grails.core.GrailsApplication
import groovy.transform.Synchronized
import org.grails.web.json.JSONArray

import java.util.concurrent.TimeUnit

import static grails.async.Promises.task


/**
 * Service to build client settings
 */
class SettingsService {
    GrailsApplication grailsApplication

    def getClientSettings() {
        def configAcceptableForExternalClient = []
        def orgaList = []
        def nodeList = []

        try {
            grailsApplication.config.appconfig.organisations.each {
                orgaList.add([name: it.name, desc: it.desc, url: it.url, logo_url: it.logo_url, multichain_id: it.multichain_id])
            }
        } catch (Exception ex) {
            log.error("Parse organisations appconfig: ${ex.message}",ex)
        }
        try {
            grailsApplication.config.appconfig.blocksy_nodes.each {
                nodeList.add([crypto_currency: it.crypto_currency, owner_pubkey: it.owner_pubkey])
            }
        } catch (Exception ex) {
            log.error("Parse blocksy_nodes appconfig: ${ex.message}",ex)
        }

        try {
            configAcceptableForExternalClient =
                    [title: grailsApplication.config.appconfig.title,
                     desc: grailsApplication.config.appconfig.desc,
                     log_display_url: grailsApplication.config.appconfig.log_display_url,
                     app_version: grailsApplication.config.appconfig.app_version,
                     blocksy_website_url: grailsApplication.config.appconfig.blocksy_website_url,
                     terms_url: grailsApplication.config.appconfig.terms_url,
                     logo_url: grailsApplication.config.appconfig.logo_url,
                     banner_url: grailsApplication.config.appconfig.banner_url,
                     default_locale: grailsApplication.config.appconfig.default_locale,
                     donation: grailsApplication.config.appconfig.donation,
                     investment: grailsApplication.config.appconfig.investment,
                     owner:[name: grailsApplication.config.appconfig.owner.name, event: grailsApplication.config.appconfig.owner.event, multichain_id: grailsApplication.config.appconfig.owner.multichain_id],
                     message: grailsApplication.config.appconfig.message,
                     twitter: [blocksy_screen_name: grailsApplication.config.appconfig.twitter.blocksy_screen_name],
                     organisation:orgaList,
                     blocksy_nodes: nodeList,

                    ]
        } catch (Exception ex) {
            log.error("Parse main appconfig : ${ex.message}",ex)
        }

        configAcceptableForExternalClient
    }

    def getBlocksyNodeConfig(String cryptoCurrency) {
        def result = null
        grailsApplication.config.appconfig.blocksy_nodes.each { node ->
            if (node.crypto_currency == cryptoCurrency) {
                result = node
            }
        }
        return result
    }
}

