@Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
import com.skype.ipc.Transport
import com.skype.api.Account
import com.skype.api.Conversation
import com.skype.api.Skype
import com.skype.ipc.TCPSocketTransport
import com.skype.util.PemReader
import java.security.cert.X509Certificate
import java.security.PrivateKey
import com.skype.ipc.TLSServerTransport
import com.skype.api.Message
import com.skype.api.Transfer
import com.skype.api.Participant
import groovy.io.FileType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import groovyx.gpars.GParsPool
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class Gbot {
    private Skype skype;
    private Transport transport
    private Account account
    private listenerByScriptMap = new ConcurrentHashMap()
    private def config
    private def dataPath = './data'
    private def notifyList = new CopyOnWriteArrayList()

    class PublishTimerEvent extends TimerTask{
        @Override
        void run() {
            publish([type:'TIMER', conversations: notifyList])
        }
    }

    Gbot() {
        def data = loadData('gbot')
        config = data.config
        skype = new Skype()
    }

    def init(globalListener) {
        PemReader donkey = new PemReader(config.pemFileName)
        X509Certificate c = donkey.getCertificate()
        PrivateKey p = donkey.getKey()

        println("Connecting to skypekit")

        Transport t = new TCPSocketTransport(config.inetAddr, config.port)
        transport = new TLSServerTransport(t, c, p)

        skype.Init(transport)

        if (transport.isConnected()) {
            String version = skype.GetVersionString()
            println "SkypeKit Version: " + version
            skype.Start()

        } else {
            println("Error connecting to skypekit")
            System.exit(1)
        }

        account = skype.GetAccount(config.login)
        account.LoginWithPassword(config.password, false, false)

        skype.RegisterListener(Skype.getmoduleid(), globalListener)

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new PublishTimerEvent(), config.timerPeriod, config.timerPeriod)
    }

    def publish(event){
        GParsPool.withPool() {
            listenerByScriptMap.keySet().each { scriptName ->
                listenerByScriptMap[scriptName][event.type].each { listener ->
                    listener.callAsync(event);
                }
            }
        }
    }

    def registerListeners(scriptName, listenerMap) {
        listenerByScriptMap[scriptName] = listenerMap
    }

    def sendMessage(conversation, message){
        conversation.SetMyTextStatusTo(Participant.TEXT_STATUS.WRITING)
        conversation.PostText(message, false)
    }

    def saveData(name, data) {
        def json = new JsonBuilder(data)
        new File(dataPath + File.separatorChar + name + '.json') << json.toPrettyString()
    }

    def loadData(name) {
        def json = new JsonSlurper()
        return json.parse(new FileReader(dataPath + File.separatorChar + name + '.json'))
    }

}

bot = new Gbot()

class GlobalListener implements Skype.SkypeListener {
    private Gbot bot

    void OnNewCustomContactGroup(com.skype.api.ContactGroup contactGroup) {

    }

    void OnContactOnlineAppearance(com.skype.api.Contact contact) {

    }

    void OnContactGoneOffline(com.skype.api.Contact contact) {

    }

    void OnConversationListChange(Conversation conversation, Conversation.LIST_TYPE list_type, boolean b) {

    }

    void OnMessage(Message message, boolean b, Message message1, Conversation conversation) {
        def event = [:]
        event.type = "MESSAGE"
        event.author = message.GetStrProperty(Message.PROPERTY.author)
        if(event.author == bot.config.login){
            return;
        }
        def type = message.GetIntProperty(Message.PROPERTY.type)
        event.messageType = Message.TYPE.get(type)
        event.conversation = conversation
        if (type == Message.TYPE.POSTED_FILES.getId()) {
            Transfer [] transfers = message.GetTransfers()
            event.files = transfers.collect { it.GetStrProperty(Transfer.PROPERTY.filename)}
        }
        if (type == Message.TYPE.POSTED_TEXT.getId() || type == Message.TYPE.POSTED_SMS.getId()) {
            event.text = message.GetStrProperty(Message.PROPERTY.body_xml);
        }
        bot.publish(event);
    }

    void OnAvailableVideoDeviceListChange() {

    }

    void OnH264Activated() {

    }

    void OnQualityTestResult(Skype.QUALITYTESTTYPE qualitytesttype, Skype.QUALITYTESTRESULT qualitytestresult, String s, String s1, String s2) {

    }

    void OnAvailableDeviceListChange() {

    }

    void OnNrgLevelsChange() {

    }

    void OnProxyAuthFailure(Skype.PROXYTYPE proxytype) {

    }
}

globalListener = new GlobalListener(bot: bot)

bot.init(globalListener)



String pluginRoot = './plugins'
def reloadPeriod = 5000

def engine = new GroovyScriptEngine(pluginRoot)
engine.getConfig().setMinimumRecompilationInterval(0);
def plugins = [:]
def dir = new File(pluginRoot)

while (true) {
    dir.eachFileRecurse(FileType.FILES) { file ->
        if (!plugins.containsKey(file.name) || ((new Date()).time - file.lastModified() <= reloadPeriod)) {
            println "loading ${file.name}"
            scriptClass = engine.loadScriptByName(file.name)
            scriptInstance = scriptClass.newInstance()
            plugins.put(file.name, scriptInstance)
            def listeners = scriptInstance.init(bot)
            bot.registerListeners(file.name, listeners)
            println "loaded ${file.name}"
        }
    }
    sleep(reloadPeriod)
}

