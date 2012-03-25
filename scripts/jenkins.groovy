@Grapes([
@Grab('org.codehaus.groovy.modules.http-builder#http-builder;0.5.2'),
@GrabExclude('xml-apis#xml-apis'),
@GrabExclude('org.codehaus.groovy#groovy'),
]) import groovyx.net.http.HTTPBuilder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import static groovyx.net.http.ContentType.JSON

class jenkins {
    def bot
    def data

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

    String doHttpRequest(url, type){
        def http = new HTTPBuilder(url)
        try {
            http.request(type, TEXT ) { req ->
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

    def build = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+build[\s]+(.*)/
        if (matcher.size() > 0) {
            def buildName = data.builds[matcher[0][2]];
            def result = doHttpRequest(data.baseUrl + "job/" + buildName + "/build", POST)
            bot.sendMessage(event.conversation, result)
        }
    }


    def status = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+status[\s]+(.*)/
        def buildName = data.builds[matcher[0][2]];
        def result = "";
        if (matcher.size() > 0) {
            def http = new HTTPBuilder(data.baseUrl + "job/" + buildName + "/api/json")
            try {
                http.request(GET, JSON ) { req ->
                    req.getParams().setParameter("http.connection.timeout", new Integer(3000));
                    req.getParams().setParameter("http.socket.timeout", new Integer(3000));

                    response.success = { resp, json ->
                        result = json.color;
                    }
                    response.failure = { resp ->
                        result = resp.statusLine
                    }
                }
            }catch (Exception e){
                result =  e.toString()
            }
        }
        result = result.endsWith("_anime") ? result.replace("_anime", " in progress") : result
        bot.sendMessage(event.conversation, result);
    }

    def init(bot) {
        this.bot = bot
        this.data = bot.loadData(this.class.name)
        TrustManager[] trustAllCerts = new TrustManager[1]
        trustAllCerts[0] = new TrustManager()
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {

        }
        return ['MESSAGE' : [status, build]]
    }
}