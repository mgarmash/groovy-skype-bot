class notify {
    def bot;
    def conversationList = []

    def toggleNotify = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+notify[\s]+(.*)/
        if (matcher.size() > 0) {
            if(matcher[0][2] == 'on'){
                conversationList.add(event.conversation)
                bot.sendMessage(event.conversation, 'notification is on')
            } else if(matcher[0][2] == 'off'){
                conversationList.remove(event.conversation)
                bot.sendMessage(event.conversation, 'notification is off')
            } else {
                bot.sendMessage(event.conversation, 'use \'gbot notify on|off|all|error\' syntax')
            }
        }
    }

    def notify = { event ->
        conversationList.each { conversation ->
            bot.sendMessage(conversation, event.text)
        }
    }

    def init(bot) {
        this.bot = bot
        return ['MESSAGE' : [toggleNotify],
                'NOTIFY' : [notify]
        ]
    }

}