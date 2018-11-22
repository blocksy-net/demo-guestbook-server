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
import grails.converters.JSON
import grails.util.BuildSettings
import grails.util.Environment
import net.logstash.logback.composite.loggingevent.StackTraceJsonProvider
import net.logstash.logback.fieldnames.LogstashFieldNames
import net.logstash.logback.stacktrace.ShortenedThrowableConverter
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter

import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import net.logstash.logback.encoder.LogstashEncoder

import java.nio.charset.Charset

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

def HOSTNAME=hostname //needed to have hostname in all scopes
def targetDir = BuildSettings.TARGET_DIR
def HOME_DIR = "."
def INSTANCE = System.getProperty("user.dir")
INSTANCE = INSTANCE.substring(INSTANCE.lastIndexOf(File.separator)+1)

appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        charset = Charset.forName('UTF-8')

        pattern =
                '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' + // Date
                        '%clr(%5p) ' + // Log level
                        '%clr(---){faint} %clr([%15.15t]){faint} ' + // Thread
                        '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' + // Logger
                        '%m%n%wex' // Message
    }
}


if (Environment.isDevelopmentMode() && targetDir != null) {
    appender("FULL_STACKTRACE", FileAppender) {
        file = "${targetDir}/stacktrace.log"
        append = true
        encoder(PatternLayoutEncoder) {
            pattern = "%level %logger - %msg%n"
        }
    }
    logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
}

def customJsonFields = '{"appname": "blocksy-guestbook", "host": "' + HOSTNAME + '", "instance": "' + INSTANCE +'"}'

appender("ROLLING", RollingFileAppender) {
    encoder(LogstashEncoder) {
        timeZone = 'UTC'
        shortenedLoggerNameLength = 35
        customFields = customJsonFields
        fieldNames(LogstashFieldNames) {
            timestamp = '@time'
            version = '[ignore]'
            message = 'msg'
            logger = 'logger'
            thread = 'thread'
            levelValue = '[ignore]'
        }

        throwableConverter(ShortenedThrowableConverter) {
            maxDepthPerThrowable = 20
            maxLength = 8192
            shortenedClassNameLength = 35
            exclude = /sun\..*/
            exclude = /java\..*/
            exclude = /groovy\..*/
            exclude = /com\.sun\..*/
            rootCauseFirst = true
        }
    }
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = 'log.json.-%d{yyyy-MM-dd}'
        maxHistory = 3
        cleanHistoryOnStart = true
    }
}

root(INFO, ['STDOUT','ROLLING'])
