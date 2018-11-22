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

class User implements Validateable {
    String email
    String name
    String company
    String pin
    String user_lang
    String twitter_screenName
    String twitter_id
    String twitter_accessToken
    String photo_url

    String message
    String crypto_currency

    static constraints = {
        email email:true, blank:false
        name blank:false
        company nullable:true
        pin blank:false, size: 4..10
        user_lang blank:false, inList: ["fr_FR", "en_US"]
        twitter_id nullable:true
        twitter_screenName nullable:true
        twitter_accessToken nullable:true
        photo_url nullable:true
        message nullable:true
        crypto_currency blank:false
    }

    String toString() {
        return "email: '${email}', name: '${name}', company: '${company}', user_lang: '${user_lang}', twitter_screenName: '${twitter_screenName}', twitter_id: '${twitter_id}', twitter_accessToken: '${twitter_accessToken}', photo_url: '${photo_url}', crypto_currency: '${crypto_currency}'"
    }
}
