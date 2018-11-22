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

import grails.util.Environment

class UrlMappings {

    static mappings = {
        if (Environment.current == Environment.PRODUCTION) {
            '/'(uri: '/index.html')
        } else {
            '/'(controller: 'application', action:'index')
        }
        "500"(view: '/error')
        "404"(view: '/notFound')

        group '/api', {
            get '/messages'(controller: 'api', action: 'listMessages')
            post '/users'(controller: 'api', action: 'createUser')
            get '/donate'(controller: 'api', action: 'sendPayment')
            get "/donations/$association_multichain_id/balance"(controller: 'api', action: 'associationBalance')
            get "/donations/balance"(controller: 'api', action: 'associationsBalance')
            get "/donations/$association_multichain_id"(controller: 'api', action: 'associationTransactions')
            get '/donations'(controller: 'api', action: 'associationsTransactions')
            get '/settings'(controller: 'api', action: 'settings')

            get '/hello'(controller: 'api', action: 'hello')
            get '/results'(controller: 'api', action: 'results')
            post '/validation'(controller: 'api', action: 'validation')
        }

    }
}

