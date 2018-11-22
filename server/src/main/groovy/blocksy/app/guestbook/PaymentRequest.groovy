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

import grails.validation.Validateable

class PaymentRequest implements Validateable {
    String user_pub_key
    String user_priv_key
    String association_pub_key
    String user_name
    String user_email
    String user_lang
    String twitter_id
    String operation
    String owner_multichain_id
    String crypto_currency

   static constraints = {
       user_pub_key blank:false
       user_priv_key blank:false
       association_pub_key blank:false
       user_name blank:false
       user_email blank:false
       user_lang blank:false, inList: ["fr_FR", "en_US"]
       twitter_id nullable:true
       operation blank:false
       owner_multichain_id blank:false
       crypto_currency blank:false
    }

    String toString() {
        return "user_pub_key: '${user_pub_key}', user_priv_key: '${user_priv_key}', association_pub_key: '${association_pub_key}, user_name: '${user_name}', user_email: '${user_email}', user_lang: '${user_lang}', twitter_id: '${twitter_id}', operation: '${operation}',crypto_currency: '${crypto_currency}', owner_multichain_id: '${owner_multichain_id}'"
    }

}
