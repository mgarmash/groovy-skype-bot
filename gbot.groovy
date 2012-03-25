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
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Gbot {
    private dataPath = './data'
    private scriptsPath = './scripts'
    private scripts = [:]
    private Skype skype;
    private Transport transport
    private Account account
    private listenersByScriptName = new ConcurrentHashMap()
    private engine
    private config
    ExecutorService executorService


    def init(globalListener) {
        def data = loadData('gbot')
        config = data.config
        skype = new Skype()
        executorService = Executors.newFixedThreadPool(10);

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

        engine = new GroovyScriptEngine(scriptsPath)
        engine.getConfig().setMinimumRecompilationInterval(0)

        new File(scriptsPath).eachFileMatch(FileType.FILES, ~/.*\.groovy/) { file ->
            loadScript(file)
        }

    }

    private void loadScript(File file) {
        println "loading ${file.name}"
        def scriptClass = engine.loadScriptByName(file.name)
        def scriptInstance = scriptClass.newInstance()
        scripts.put(file.name, scriptInstance)
        def listeners = scriptInstance.init(this)
        listenersByScriptName[file.name] = listeners
        println "loaded ${file.name}"
    }

    def publish(event){
        listenersByScriptName.keySet().each { scriptName ->
            listenersByScriptName[scriptName][event.type].each { listener ->
                executorService.submit(new Runnable(){
                    void run() {
                        listener.call(event);
                    }
                })
            }
        }
    }


    def registerListeners(scriptName, listenerMap) {
        listenersByScriptName[scriptName] = listenerMap
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

while (true) {
    sleep(1000)
}

