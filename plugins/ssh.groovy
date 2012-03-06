@Grapes([
@Grab('net.schmizz#sshj;0.7.0'),
@Grab('org.bouncycastle#bcprov-jdk16;1.46'),
@GrabExclude('xml-apis#xml-apis'),
@GrabExclude('org.codehaus.groovy#groovy'),
])
import java.security.PublicKey
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Command
import net.schmizz.sshj.transport.verification.HostKeyVerifier

class ssh {
    def TIMEOUT = 20000
    def bot;

    String sshExec(user, command, host, port=22){
        final SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(
                new HostKeyVerifier() {
                    public boolean verify(String arg0, int arg1, PublicKey arg2) {
                        return true;
                    }
                }
        )
        try {
            ssh.connect(host, port)
            ssh.authPublickey(user)
            final Session session = ssh.startSession()
            try {
                final Command cmd = session.exec(command)
                def is = cmd.getInputStream()
                def time = new Date().time
                int n;
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                while (((n = is.read()) != -1)&& (new Date().time - time < TIMEOUT)){
                    baos.write(n)
                }
                return baos.toString()
            } catch (Exception e) {
                return e.toString();
            } finally {
                if(session.isOpen()){
                    session.close()
                }
            }
        } catch (Exception e) {
            return e.toString();
        } finally {
            if(ssh.isConnected()){
                ssh.disconnect()
            }
        }
    }

    def sshTest = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+ssh[\s]+(.*)/
        if (matcher.size() > 0) {
            bot.sendMessage(event.conversation, sshExec("user", matcher[0][2], "127.0.0.1"))
        }
    }
    def init(bot) {
        this.bot = bot
        return ['MESSAGE' : [sshTest]]
    }

}