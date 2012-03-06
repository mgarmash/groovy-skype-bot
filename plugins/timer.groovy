class timer {
    def bot;
    def conversationList = []

    def toggleNotify = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+notify[\s]+(.*)/
        if (matcher.size() > 0) {
            if(matcher[0][2] == 'on'){
                bot.notifyList.add(event.conversation)
                bot.sendMessage(event.conversation, 'notification is on')
            } else if(matcher[0][2] == 'off'){
                bot.notifyList.remove(event.conversation)
                bot.sendMessage(event.conversation, 'notification is off')
            } else {
                bot.sendMessage(event.conversation, 'use \'gbot notify on|off\' syntax')
            }
        }
    }

    def init(bot) {
        this.bot = bot
        return ['MESSAGE' : [toggleNotify]]
    }

}