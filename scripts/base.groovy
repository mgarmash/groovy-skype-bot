class base {
    def bot;

    def print = { event ->
        println "${event.author} : ${event.text}"
    }

    def echo = { event ->
        def matcher = event.text =~ /^(gbot)[\s]+echo[\s]+(.*)/
        if (matcher.size() > 0) {
            bot.sendMessage(event.conversation, matcher[0][2])
        }
    }


    def init(bot) {
        this.bot = bot
        return ['MESSAGE' : [print, echo]]
    }

}