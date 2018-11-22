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
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.util.StopWatch
import org.springframework.web.client.ResourceAccessException

import twitter4j.DirectMessage;
import twitter4j.Query;
import twitter4j.QueryResult;
//import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.StatusDeletionNotice;
//import twitter4j.StatusListener;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
//import twitter4j.TwitterStream;
//import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder

import javax.imageio.ImageIO
import java.awt.image.BufferedImage;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.MessageDigest
import java.time.Instant
import java.util.Formatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Service needed to call Blocksy API
 */
class TwitterService {
    GrailsApplication grailsApplication

    RestBuilder rest = new RestBuilder()

    private final static String TWITTER_API_URL = "https://api.twitter.com/1.1"
    private final static String PROTECTED_RESOURCE_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";
    private final static Integer MAX_TWEET_SIZE = 280

    def static hmac(String data, String key) throws java.security.SignatureException
    {
        String result
        try {
            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1");
            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes());
            result= rawHmac.encodeBase64()
        } catch (Exception e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result
    }

    def static oauth(String method, String url, LinkedHashMap params, String consumerSecret, String tokenSecret ){

        // retrieve arguments of the target and split arguments
        TreeMap map = [:]

        for (String item : params) {
            def (key, value) = item.tokenize('=')
            map.put(key, value)
        }

        map.put("oauth_consumer_key", consumerSecret)
        map.put("oauth_nonce", UUID.randomUUID().toString().replaceAll("-", ""))
        map.put("oauth_signature_method", "HMAC-SHA1")
        map.put("oauth_timestamp", ""+ (int) (System.currentTimeMillis()/1000))
        map.put("oauth_token", tokenSecret)
        map.put("oauth_version", "1.0")

        String.metaClass.encode = {
            java.net.URLEncoder.encode(delegate, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        }

        String parameterString = map.collect { String key, String value ->
            "${key.encode()}=${value.encode()}"
        }.join("&")

        String signatureBaseString = ""

        signatureBaseString += method.toUpperCase()
        signatureBaseString += '&'
        signatureBaseString += url.encode()
        signatureBaseString += '&'
        signatureBaseString += parameterString.encode()

        String signingKey = consumerSecret.encode() + '&' + tokenSecret.encode()

        String oauthSignature = hmac(signatureBaseString, signingKey)

        String oauth = 'OAuth '
        oauth += 'oauth_consumer_key="'
        oauth += map.oauth_consumer_key.encode()
        oauth += '", '
        oauth += 'oauth_nonce="'
        oauth += map.oauth_nonce.encode()
        oauth += '", '
        oauth += 'oauth_signature="'
        oauth += oauthSignature.encode()
        oauth += '", '
        oauth += 'oauth_signature_method="'
        oauth += map.oauth_signature_method.encode()
        oauth += '", '
        oauth += 'oauth_timestamp="'
        oauth += map.oauth_timestamp.encode()
        oauth += '", '
        oauth += 'oauth_token="'
        oauth += map.oauth_token.encode()
        oauth += '", '
        oauth += 'oauth_version="'
        oauth += map.oauth_version.encode()
        oauth += '"'

        return oauth.replaceAll("\"","'")
    }


    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    private static String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();

        for (byte b : bytes) {
            formatter.format("%02x", b);
        }

        return formatter.toString();
    }

    private def calculateRFC2104HMAC(String data, String key)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        return toHexString(mac.doFinal(data.getBytes()));
    }

    def decrypt(String encryptedData){

        log.info encryptedData

        final String TWITTER_CONSUMER_KEY = grailsApplication.config.getProperty('appconfig.twitter.consumer_key', String)
        final String TWITTER_CONSUMER_SECRET = grailsApplication.config.getProperty('appconfig.twitter.consumer_secret', String)

        InputStream cipherInputStream = null;
        String output = null
        TreeMap map = [:]

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(TWITTER_CONSUMER_SECRET.getBytes(StandardCharsets.UTF_8));

            final byte[] secretKey = hash;
            final byte[] initVector = TWITTER_CONSUMER_KEY.substring(4,20).getBytes(StandardCharsets.UTF_8)

            final byte[] encryptedDataB64 = Base64.getDecoder().decode(encryptedData);

            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new IvParameterSpec(initVector, 0, cipher.getBlockSize()));

            //byte[] decodedValue = new Base64().decode(encryptedData.getBytes(StandardCharsets.UTF_8))
            output = new String(cipher.doFinal(encryptedDataB64))

            // fix result
            // find access access_token
            output = output.substring(output.indexOf("\"")+1)
            output = output.replaceAll("\"","'")

            String accessToken = output.substring(0,output.indexOf("'"))

            // find access access_token_secret
            output = output.substring(output.indexOf("'access_token_secret':'"))
            output = output.replaceAll("'access_token_secret':'","")

            String accessTokenSecret = output.substring(0,output.indexOf("'"))

            // find access access_token_secret
            output = output.substring(output.indexOf("'screen_name':'"))
            output = output.replaceAll("'screen_name':'","")

            String screenName = output.substring(0,output.indexOf("'"))

            map.put("access_token", accessToken)
            map.put("access_token_secret", accessTokenSecret)
            map.put("screen_name", screenName)

        }
        catch (Exception ex) {
            log.error(ex.message, ex)

            // support old
            map.put("screen_name", encryptedData)
        }
        finally {
            if (cipherInputStream != null) {
                cipherInputStream.close();
            }

            return map
        }
    }


    /**
     * Post tweet (update user status)
     * @param user - user tweeting
     * @param transactionHash - transaction hash to display
     * @param auth - auth contains Twitter access token and secret token
     * @return TODO: Twitter service response.code
     */
    def tweet(User user, String transactionHash, TreeMap auth) {
        log.debug("started tweet, user ${user.name}, at ${Instant.now()}")
        StopWatch stopWatch = new StopWatch()

        def result
        TwitterFactory tf = null

        try {

            def url = TWITTER_API_URL+"/statuses/update.json"

            def isTweetActive = grailsApplication.config.getProperty('appconfig.twitter.tweet', Boolean)

            log.info "is tweet activated:" +(isTweetActive)

            if (isTweetActive) {

                def hashtags = grailsApplication.config.getProperty('appconfig.twitter.hashtags', String)
                def blocksyScreenName = grailsApplication.config.getProperty('appconfig.twitter.blocksy_screen_name', String)
                def cryptoCurrency = user.crypto_currency
                def blocktrailUrl = BlockExplorerHelper.getUri(grailsApplication, cryptoCurrency, transactionHash)
                if (blocktrailUrl == "") {
                    log.error("getUri: failed for cryptoCurrency ${cryptoCurrency}, check config file entry block_explorer_url.")
                }

                def advertUrl = grailsApplication.config.appconfig.twitter.advert_url[user.user_lang]
                def securedByBlocksy = "secured by @blocksy_net"
                def tweetUserMessage = user.message
                Integer messageSize = tweetUserMessage.length() + 1 + hashtags.length() + 1 + securedByBlocksy.length() + 1 + blocktrailUrl.length()
                def exceedingSize = (messageSize - MAX_TWEET_SIZE)
                //reduce user message in case tweet is too long
                if (exceedingSize > 0) {
                            tweetUserMessage = tweetUserMessage.substring(0, tweetUserMessage.length() - exceedingSize)
                }

                def tweet = "${tweetUserMessage} ${hashtags} ${securedByBlocksy} ${blocktrailUrl}"

                final String TWITTER_CONSUMER_KEY = grailsApplication.config.getProperty('appconfig.twitter.consumer_key', String)
                final String TWITTER_CONSUMER_SECRET = grailsApplication.config.getProperty('appconfig.twitter.consumer_secret', String)
                final String TWITTER_HTTP_PROXY = grailsApplication.config.getProperty('appconfig.twitter.http_proxy', String)

                ConfigurationBuilder cb = new ConfigurationBuilder();
                cb.setDebugEnabled(true)
                        .setOAuthConsumerKey(TWITTER_CONSUMER_KEY)
                        .setOAuthConsumerSecret(TWITTER_CONSUMER_SECRET)
                        .setOAuthAccessToken(auth.access_token)
                        .setOAuthAccessTokenSecret(auth.access_token_secret);

                //Set http proxy if set in settings
                Proxy proxyParam = null
                if (TWITTER_HTTP_PROXY) {
                    String[] proxy = TWITTER_HTTP_PROXY.split(":")
                    String host = proxy[0]
                    Integer port = 8080
                    //System.setProperty("http.proxyHost", host);
                    //System.setProperty("http.proxyPort",port.toString());

                    if (proxy.length == 2) {
                        port = Integer.parseInt(proxy[1])
                    }

                    cb.setHttpProxyHost(host).setHttpProxyPort(port)
                    proxyParam = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                } else {
                    proxyParam = Proxy.NO_PROXY
                }

                log.info "going to tweet... "

                tf = new TwitterFactory(cb.build());
                Twitter twitter = tf.getInstance();

                StatusUpdate statusUpdate = new StatusUpdate(tweet)
                //------------

                InputStream inStream = getImageStream(advertUrl, proxyParam)
                if (inStream == null && proxyParam != Proxy.NO_PROXY) {
                    proxyParam = Proxy.NO_PROXY
                    inStream = getImageStream(advertUrl, proxyParam)
                }

                if (inStream != null) {
                    long[] mediaIds=new long[1]

                    try {
                        mediaIds[0]=twitter.uploadMedia(advertUrl,inStream).getMediaId()
                        //statusUpdate.setMedia("advert", inStream);
                        statusUpdate.setMediaIds(mediaIds)
                    } finally {
                        inStream.close()
                    }
                }

//Solution 3
            /*
                try {
                    URL imgUrl = new URL(advertUrl);
                    BufferedImage image = ImageIO.read(imgUrl)
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    ImageIO.write(image, "image/png", os);
                    InputStream is = new ByteArrayInputStream(os.toByteArray());
                    statusUpdate.setMedia("advert", is);
                } catch(Exception ex) {
                    log.error ex.message
                }
*/
                //------------
                Status status = twitter.updateStatus(statusUpdate)

                log.info "... tweet posted: " + status.getText()

                tf = null

            }

        }
        catch (Exception e) {
            log.error "error while posting: " + e.getMessage()
        }
        finally {
            tf = null
        }
        log.debug("ended tweet, user ${user.name}, at ${Instant.now()}, elapsed ${stopWatch.getTotalTimeSeconds()} seconds")

        result
    }

    private InputStream getImageStream(String url, Proxy proxy) {
        InputStream inStream=null
        try  {
            //System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
            inStream=new URL(url).openConnection(proxy).getInputStream()
        }
        catch(MalformedURLException thr) { log.error("The media URL is not valid: ${url} (${thr.message})") }
        catch(IOException thr) { /*log.error("The media could not be read: ${url} (${thr.message})")*/ }
        inStream
    }
}

