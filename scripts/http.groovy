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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class http {
    def bot
    def watchList
    def tasks = new ConcurrentHashMap()
    ExecutorService executorService
    ScheduledExecutorService timer

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

    def checkUrlIsAlive = { event ->
        def matcher = Jsoup.clean(event.text, Whitelist.none()) =~ /^(gbot)[\s]+check [\s]+(.*)/
        if (matcher.size() > 0) {
            bot.sendMessage(event.conversation, httpGetStatus(matcher[0][2]))
        }
    }

    def addToWatchList = { event ->
        def matcher = Jsoup.clean(event.text, Whitelist.none()) =~ /^(gbot)[\s]+watch (?!list)[\s]*([^\s]*)[\s]+(\d*)/
        if (matcher.size() > 0) {
            def url = matcher[0][2]
            def period = matcher[0][3]
            if(watch(url, Long.valueOf(period)))
                bot.sendMessage(event.conversation, "$url is now watching")
            else
                bot.sendMessage(event.conversation, "$url was already watched")
        }
    }

    def removeFromWatchList = { event ->
        def matcher = Jsoup.clean(event.text, Whitelist.none()) =~ /^(gbot)[\s]+unwatch[\s]+(.*)/
        if (matcher.size() > 0) {
            def url = matcher[0][2]
            if(unwatch(url)){
                bot.sendMessage(event.conversation, "$url is not being watched")
            } else {
                bot.sendMessage(event.conversation, "$url was not watched")
            }

        }
    }

    def showWatchList = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+watch list/
        if (matcher.size() > 0) {
            bot.sendMessage(event.conversation, watchList.collect {it.url + ' - ' + it.period + ' sec.'}.join("\n"))
        }
    }

    def unwatch(url){
        def timerTask = tasks.get(url)
        if (timerTask){
            timerTask.cancel(true)
            tasks.remove(timerTask)
            def item = watchList.find {it.url == url}
            watchList.remove(item)
            bot.saveData(this.class.name, watchList)
            return true;
        }
        return false;
    }

    class StatusTask implements Runnable {
        def url
        def bot
        void run() {
            executorService.submit(new Runnable(){
                void run() {
                    def status = httpGetStatus(url)
                    bot.publish(['type': 'NOTIFY', 'text': "$url $status"])
                }
            })
        }
    }


    boolean watch(url, long period){
        if(!tasks.containsKey(url)){
            Runnable task = new Runnable() {
                public void run() {
                    executorService.submit(new StatusTask(url: url, bot: bot));
                }
            }
            def timerTask = timer.scheduleWithFixedDelay(task, period, period, TimeUnit.SECONDS);
            tasks.put(url, timerTask)
            bot.saveData(this.class.name, watchList)
            if(!watchList.find{it.url == url}){
                watchList.add(["url" : url, "period" : period])
            }
            return true;
        }
        return false;
    }

    def init(bot) {
        this.bot = bot
        def data = bot.loadData(this.class.name)
        watchList = data.watchList
        timer = java.util.concurrent.Executors.newScheduledThreadPool(10);
        executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        watchList.each { item ->
            watch(item.url, Long.valueOf(item.period))
        }
        return ['MESSAGE' : [checkUrlIsAlive, addToWatchList, removeFromWatchList, showWatchList]]
    }
}