@Grapes([
@Grab('org.codehaus.groovy.modules.http-builder#http-builder;0.5.2'),
@Grab(group='org.jsoup', module='jsoup', version='1.6.1'),
@GrabExclude('xml-apis#xml-apis'),
@GrabExclude('org.codehaus.groovy#groovy'),
])
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HttpsURLConnection

class http {
    def bot;
    def checkList

    class TrustManager implements X509TrustManager {

        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs, String authType) {
        }

    }


    String httpGetStatus(url){
        TrustManager[] trustAllCerts = new TrustManager[1]
        trustAllCerts[0] = new TrustManager()
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {

        }

        def http = new HTTPBuilder(url)
        try {
            http.request( GET, TEXT ) { req ->
                req.getParams().setParameter("http.connection.timeout", new Integer(3000));
                req.getParams().setParameter("http.socket.timeout", new Integer(3000));

                response.success = { resp, reader ->
                    return 'OK'
                }
                response.failure = { resp ->
                    return resp.statusLine
                }
            }
        }catch (Exception e){
            return e.toString()
        }
    }

    def httpPing = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+hping[\s]+(.*)/
        if (matcher.size() > 0) {
            bot.sendMessage(event.conversation, httpGetStatus(Jsoup.clean(matcher[0][2], Whitelist.none())))
        }
    }

    def httpCheck = { event ->
        checkList.each { host ->
            def status = httpGetStatus(host)
            if(status != 'OK'){
                event.conversations.each { conversation ->
                    bot.sendMessage(conversation, "$host: $status")
                }
            }
        }
    }

    def init(bot) {
        this.bot = bot

        def data = bot.loadData(this.class.name)
        checkList = data.checkList

        return ['MESSAGE' : [httpPing],
                'TIMER' : [httpCheck]
        ]
    }

}