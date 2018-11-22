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

package blocksy.logback;

import grails.plugins.rest.client.RestBuilder
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import grails.converters.JSON;
import grails.plugins.rest.client.RestResponse;

public class SlackAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static Layout<ILoggingEvent> defaultLayout = new LayoutBase<ILoggingEvent>() {
        public String doLayout(ILoggingEvent event) {
            return "-- [" + event.getLevel() + "]" +
                    event.getLoggerName() + " - " +
                    event.getFormattedMessage().replaceAll("\n", "\n\t");
        }
    };

    private String proxy;
    private String proxyPort;
    private String webhookUri;
    private String channel;
    private String username;
    private String iconEmoji;
    private String iconUrl;
    private Boolean colorCoding = false;
    private Layout<ILoggingEvent> layout = defaultLayout;

    @Override
    protected void append(final ILoggingEvent evt) {
        try {
            if (webhookUri != null && !webhookUri.isEmpty()) {
                sendMessageWithWebhookUri(evt);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            addError("Error posting log to Slack.com (" + channel + "): " + evt, ex);
        }
    }

    private void sendMessageWithWebhookUri(final ILoggingEvent evt) throws IOException {
        String[] parts = layout.doLayout(evt).split("\n", 2);

        def message = [:]
        message.put("channel", channel);
        message.put("username", username);
        message.put("icon_emoji", iconEmoji);
        message.put("icon_url", iconUrl);
        message.put("text", parts[0]);

        // Send the lines below the first line as an attachment.
        if (parts.length > 1 && parts[1].length() > 0) {
            Map<String, String> attachment = new HashMap<>();
            attachment.put("text", parts[1]);
            if (colorCoding) {
                attachment.put("color", colorByEvent(evt));
            }

            message.put("attachments", Collections.singletonList(attachment));
        }

        postMessage(webhookUri, "application/json", message);
    }

    private String colorByEvent(ILoggingEvent evt) {
        if (Level.ERROR.equals(evt.getLevel())) {
            return "danger";
        } else if (Level.WARN.equals(evt.getLevel())) {
            return "warning";
        } else if (Level.INFO.equals(evt.getLevel())) {
            return "good";
        }

        return "";
    }

    private void postMessage(String uri, String contentType, Map message) throws IOException {
        try {
            RestBuilder rest
            if (proxy) {
                rest = new RestBuilder(proxy: ["${proxy}": proxyPort])
            } else {
                rest = new RestBuilder()
            }
            def json = message as JSON

            RestResponse restResponse = rest.post(uri) {
                body(json) accept contentType
            }

        } catch (Exception ex) {
            ex.printStackTrace()
        }
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(final String channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    public void setIconEmoji(String iconEmojiArg) {
        this.iconEmoji = iconEmojiArg;
        if (iconEmoji != null && !iconEmoji.isEmpty() && iconEmoji.startsWith(":") && !iconEmoji.endsWith(":")) {
            iconEmoji += ":";
        }
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrlArg) {
        this.iconUrl = iconUrlArg;
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(final Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    public String getWebhookUri() {
        return webhookUri;
    }

    public void setWebhookUri(String webhookUri) {
        this.webhookUri = webhookUri;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }


    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
    public Boolean getColorCoding() {
        return colorCoding;
    }

    public void setColorCoding(Boolean colorCoding) {
        this.colorCoding = colorCoding;
    }
}
