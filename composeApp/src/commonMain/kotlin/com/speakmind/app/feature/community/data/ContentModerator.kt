package com.speakmind.app.feature.community.data

enum class ViolationType(val userMessage: String) {
    SEXUAL("Sexual or explicit content is not allowed in this community."),
    DATING("Dating and flirting aren't allowed. Speaky is for practising English."),
    HATE_SPEECH("Hate speech and discrimination are not tolerated here."),
    VIOLENCE("Violent or threatening content is not allowed."),
    SELF_HARM("Content promoting self-harm is not allowed."),
    DRUGS("Drug-related content is not allowed."),
    CONTACT_HARVESTING("Sharing personal contact info, handles, or links is not allowed."),
    PROFANITY("Please keep your language clean and respectful."),
    SCAM("Scam or money-related solicitation is not allowed."),
    EXTERNAL_LINK("Sharing external links is not allowed in this community."),
    PERSONAL_INFO("Sharing personal information is not allowed here."),
    GAMBLING("Gambling-related content is not allowed."),
}

data class ModerationResult(
    val isAllowed: Boolean,
    val violationType: ViolationType? = null,
)

object ContentModerator {

    fun check(text: String): ModerationResult {
        // Phase 0: phone numbers hidden with separators, words, Arabic or emoji digits.
        if (hasHiddenNumber(text)) return ModerationResult(isAllowed = false, violationType = ViolationType.CONTACT_HARVESTING)
        // Phase 1: raw text checks (emails, URLs, @handles — must run before normalization
        // because normalization strips @ and dots that are needed for pattern matching)
        for ((type, patterns) in RAW_RULES) {
            if (patterns.any { it.containsMatchIn(text) }) {
                return ModerationResult(isAllowed = false, violationType = type)
            }
        }
        // Phase 2: normalized text checks (leet-speak, obfuscated content)
        val lower = normalize(text)
        // Phase 3: words spelled letter by letter ("f u c k") are joined back up and checked too.
        val spelled = SPELLED_OUT.findAll(lower).joinToString(" ") { it.value.filter(Char::isLetter) }
        for (candidate in listOf(lower, spelled)) {
            for ((type, patterns) in NORMALIZED_RULES) {
                if (patterns.any { it.containsMatchIn(candidate) }) {
                    return ModerationResult(isAllowed = false, violationType = type)
                }
            }
        }
        return ModerationResult(isAllowed = true)
    }

    private val SPELLED_OUT = Regex("""(?:\b[a-z]\b\s*){3,}""")

    private val NUMBER_WORDS = mapOf(
        "zero" to '0', "oh" to '0', "one" to '1', "two" to '2', "three" to '3', "four" to '4',
        "five" to '5', "six" to '6', "seven" to '7', "eight" to '8', "nine" to '9',
    )

    /**
     * 8+ digits with only spaces, symbols or number words between them ("0/5/0 1 2 3 4 5",
     * "zero five zero…", "٠٥٠١٢٣٤٥", "0️⃣5️⃣…"): almost always a phone number.
     */
    internal fun hasHiddenNumber(text: String): Boolean {
        val digits = StringBuilder()
        for (ch in text) {
            digits.append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')          // Arabic-Indic
                    in '۰'..'۹' -> '0' + (ch - '۰')          // Persian / Urdu
                    in '０'..'９' -> '0' + (ch - '０')        // full width
                    in '①'..'⑨' -> '1' + (ch - '①')          // circled
                    '\uFE0F', '\u20E3' -> continue          // emoji keycap parts
                    else -> ch
                },
            )
        }
        // Number words become digits; every other word separates runs.
        val tokens = Regex("""[a-zA-Z]+|\d|[^a-zA-Z\d]""").findAll(digits.toString().lowercase()).map { it.value }
        var run = 0
        for (t in tokens) {
            val d = NUMBER_WORDS[t]?.toString() ?: t.takeIf { it.length == 1 && it[0].isDigit() }
            when {
                d != null -> { run++; if (run >= 8) return true }
                t[0].isLetter() -> run = 0
                else -> Unit // spaces and symbols between digits don't break the run
            }
        }
        return false
    }

    // Normalize leet-speak and obfuscation tricks
    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]"), "") // zero-width / soft-hyphen
            .replace("*", "")                                       // f*ck, s*x
            .replace(Regex("(.)\\1{2,}"), "$1$1")                  // collapse 3+ repeated chars
            .replace('0', 'o')
            .replace('3', 'e')
            .replace('1', 'i')
            .replace('!', 'i')
            .replace('$', 's')
            .replace('+', 't')
            .replace('5', 's')
            .replace('4', 'a')
            .replace('7', 't')
            .replace('8', 'b')
            .replace('9', 'g')
            .replace("|", "i")
            .replace("ph", "f")
            .replace(Regex("[^a-z0-9\\s]"), " ") // strip remaining special chars
    }

    // ── RAW RULES (run on original text, before normalization) ──────────────

    private val RAW_RULES: List<Pair<ViolationType, List<Regex>>> = listOf(

        ViolationType.EXTERNAL_LINK to listOf(
            // HTTP/HTTPS links
            Regex("""https?://\S{4,}""", RegexOption.IGNORE_CASE),
            // www. links
            Regex("""\bwww\.\S{4,}""", RegexOption.IGNORE_CASE),
            // Common URL shorteners
            Regex("""\b(?:bit\.ly|tinyurl\.com|t\.co|goo\.gl|ow\.ly|short\.io|rb\.gy|cutt\.ly)/\S+""", RegexOption.IGNORE_CASE),
            // Platform short links
            Regex("""\b(?:wa\.me|t\.me|ig\.me|discord\.gg|fb\.me|linktr\.ee|lnkd\.in)/\S*""", RegexOption.IGNORE_CASE),
            // Domain-like patterns (word.com/net/org/io/app)
            Regex("""\b\w{3,}\.(com|net|org|io|app|me|co|tv|gg|ly|link)\b""", RegexOption.IGNORE_CASE),
        ),

        ViolationType.CONTACT_HARVESTING to listOf(
            // Email addresses
            Regex("""\b[\w.+\-]{2,}@[\w\-]{2,}\.[a-zA-Z]{2,}\b"""),
            // @username handles (3+ chars, not mid-word)
            Regex("""(?<!\w)@[a-zA-Z0-9_.]{3,}"""),
            // Phone numbers — international and local formats
            Regex("""\b\+?[\d][\d\s.\-()]{7,}[\d]\b"""),
            // "number is / my number" with digits following
            Regex("""(?:my\s+(?:number|no|phone|cell|mobile|whatsapp)\s*(?:is|:)?\s*)[\d\s()+\-]{6,}""", RegexOption.IGNORE_CASE),
        ),

        ViolationType.SEXUAL to listOf(
            Regex("""سكس|نيكني|انيكك|منيوك|شرموط|عاهرة|قحبة|سحاق|سحاقية|بزاز|طيزك|نهودك|عارية|صور عارية|مثليين|المثلية|مثلي الجنس|شواذ|شاذ جنسي|لوطي|ميول جنسية|ليزبيان"""),
        ),

        ViolationType.DATING to listOf(
            Regex("""حبيبتي|تتزوجيني|تتجوزيني|زواج|ابحث عن زوجة|ابحث عن زوج|ابغى زوجة|ابي زوجة|عايز اتجوز|ابغى اتزوج|ابي اتزوج|عروسة|عريس|مواعدة|بحبك|احبك|ابغى بنت|ابي بنت|عايز بنت|للتعارف|تعارف|صورتك|ارسلي صورة|ابعتي صورة|بوسة|بوسني"""),
        ),

        ViolationType.CONTACT_HARVESTING to listOf(
            Regex("""واتس|وتساب|انستا|انستغرام|انستقرام|سناب|تيك توك|تلجرام|تيليجرام|تليجرام|فيسبوك|فيس بوك|رقمك|رقم تلفونك|ارسل رقم|ابعت رقم|كلمني خاص|تواصل معي|ضيفني|برنامج ثاني|برنامج تاني|برنامج اخر|برنامج آخر|تطبيق ثاني|تطبيق تاني|تطبيق اخر|تطبيق آخر|خارج التطبيق|برا التطبيق|بره التطبيق|نكمل برا|نكمل بره|نتكلم برا|كلمني برا|ايش برامجك|وش برامجك|عندك سوشيال|زانجي|زنجي|ايمو|بوتيم|سيجنال|تانجو|بيجو|ازار|زووم|هالو تولك|سكايب|ديسكورد|تيليقرام"""),
        ),

        ViolationType.PERSONAL_INFO to listOf(
            // Social security / national ID patterns
            Regex("""\b\d{3}[-\s]?\d{2}[-\s]?\d{4}\b"""),              // SSN
            Regex("""\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b"""),   // card number
            // Home address indicators
            Regex("""\b\d{1,5}\s+\w+\s+(?:street|st|avenue|ave|road|rd|lane|ln|drive|dr|blvd)\b""", RegexOption.IGNORE_CASE),
        ),
    )

    // ── NORMALIZED RULES (run on leet-speak-decoded lowercase text) ─────────

    private val NORMALIZED_RULES: List<Pair<ViolationType, List<Regex>>> = listOf(

        // Word boundaries keep everyday words (title, grape, Essex, peacock, vibration…) allowed.
        ViolationType.SEXUAL to listOf(
            Regex("""\b(?:fuck|fuk|fck|fuq)"""),
            Regex("""\bsex(?:y|ting|ual|cam)?\b"""),
            Regex("""\b(?:porn|porno|xxx|hentai|nsfw)"""),
            Regex("""\b(?:nude|nudes|naked)\b"""),
            Regex("""\b(?:dick|dicks|cock|cocks|penis|boner)\b"""),
            Regex("""\b(?:pussy|vagina|cunt|vulva)"""),
            Regex("""\b(?:boob|boobs|tit|tits|titties|nipples?)\b"""),
            Regex("""\b(?:asshole|anus|butthole)\b"""),
            Regex("""\b(?:dildo|vibrator|masturbat)"""),
            Regex("""\b(?:orgasm|cumshot|ejaculat)"""),
            Regex("""\b(?:blowjob|handjob|rimjob|fellatio|cunnilingus)"""),
            Regex("""\b(?:rape|raped|raping|rapist|molest)"""),
            Regex("""\b(?:horny|erection)\b"""),
            Regex("""\b(?:onlyfans|camgirl|stripper|escort|prostitut|hookup)"""),
            Regex("""\berotic|\bone night stand"""),
            Regex("""\b(?:bitch|whore|slut)"""),
            Regex("""\bpedophil|\bchild porn|\bloli\b"""),
            Regex("""\b(?:incest|bestiality)"""),
            Regex("""\b(?:seks|seggs|secks|sexx?|s e x)\b"""),
            Regex("""\bmake love|\bsleep with (?:me|you)|\bbed with me"""),
            Regex("""\b(?:kinky|fetish|bdsm|condom|lingerie|bra size)\b"""),
            Regex("""\btouch (?:me|you|your body)|\b(?:show|send) (?:me )?your body"""),
            Regex("""\b(?:wet for|turn me on|turned on|so hot for|hot girl|hot boy|sexy (?:girl|boy|lady|body))"""),
            Regex("""\bsend (?:me )?nudes?|\bnude (?:pic|foto)"""),
            Regex("""\b(?:gay|gays|lesbian|lesbians|homosexual|homosexuality|bisexual|lgbt\w*|lgbtq\w*|queer|transgender|tranny|pansexual|gay sex)\b"""),
        ),

        ViolationType.DATING to listOf(
            Regex("""\b(?:be|become) my (?:girl ?friend|boy ?friend|gf|bf|wife|husband|lover|valentine|baby|babe)"""),
            Regex("""\b(?:looking for|search(?:ing)? for|want|need|find me) (?:a |an |my )?(?:girl ?friend|boy ?friend|gf|bf|lover|girl|hot girl|date|wife|husband|bride|partner|soul ?mate|life partner|marriage)\b"""),
            Regex("""\b(?:dating|date with|online date|want to (?:get )?marr(?:y|ied)|wana (?:get )?marr(?:y|ied)|marriage proposal|propose to (?:you|me)|get married to (?:you|me))\b"""),
            Regex("""\b(?:marry me|wil+ (?:you|u) marry me|date me|go on a date with me|go out with me)\b"""),
            Regex("""\b(?:i love (?:you|u)|love (?:you|u) (?:baby|babe|dear|so much)|i miss (?:you|u) (?:baby|babe)|kis (?:me|you)|kiss (?:me|you)|hug (?:me|you) tight)\b"""),
            Regex("""\b(?:you are|you re|ur|u r) (?:so )?(?:beautiful|pretty|cute|hot|sexy|gorgeous|handsome)\b"""),
            Regex("""\bsend (?:me )?(?:your |ur |a )?(?:pic|pics|picture|foto|fotos|selfie|face)\b"""),
            Regex("""\b(?:show me|let me see) (?:your |ur )?(?:face|pic|foto|picture)\b"""),
            Regex("""\b(?:video call|vid call|cam to cam|on cam|facetime)\b"""),
            Regex("""\bany (?:girls?|ladies|women|females|boys?) here\b|\b(?:girls?|females?) only\b"""),
            Regex("""\b(?:hey|hi|hello) (?:baby|babe|sexy|beautiful|cutie|sweetheart)\b"""),
            Regex("""\b(?:habibti|habibty|my love|my dear wife|wifey|hubby)\b"""),
            Regex("""\b(?:lonely|feeling lonely) (?:girl|boy|guy|lady)|\bneed company\b"""),
        ),

        ViolationType.HATE_SPEECH to listOf(
            Regex("""\bniger|\bniga\b"""),      // post-normalize (double letters collapsed)
            Regex("""\bfagot|\bfag\b"""),
            Regex("""\bkike\b|\bjew pig|\bjew rat"""),
            Regex("""\bspic\b|\bwetback\b"""),
            Regex("""\bchink\b"""),
            Regex("""\bcamel jockey|\bterrorist muslim"""),
            Regex("""\bwhite power|\bnazi\b|\bheil hitler|\bwhite suprem"""),
            Regex("""\bblack monkey|\bgo back to africa"""),
            Regex("""\btrani|\bshemale\b"""),
            Regex("""\bhate (?:jews|muslims|christians|blacks|whites|gays)"""),
            Regex("""\bkill all (?:jews|muslims|blacks|whites|gays)"""),
            Regex("""\bgo back to your country|\bforeigners? (?:out|leave|die)"""),
        ),

        ViolationType.VIOLENCE to listOf(
            Regex("""i will kill you|gona kill you|want to kill you"""),
            Regex("""i will (?:hurt|harm|stab|shot|bomb) you"""),
            Regex("""death threat|send a bomb|blow up"""),
            Regex("""cut your throat|slit your throat"""),
            Regex("""school shooting|mas shooting|terrorist atack"""),
            Regex("""how to (?:make|build) a bomb"""),
            Regex("""beat you up|break your (?:face|legs|neck)"""),
        ),

        ViolationType.SELF_HARM to listOf(
            Regex("""kill yourself|kys\b"""),
            Regex("""suicide method|how to suicide|commit suicide"""),
            Regex("""self harm|cut myself|cuting myself"""),
            Regex("""want to die|end my life|no reason to live"""),
            Regex("""overdose on pils|lethal dose"""),
            Regex("""rope method"""),
        ),

        ViolationType.DRUGS to listOf(
            Regex("""\bheroin\b|\bcocaine\b|\bmeth\b|\bcrack cocaine"""),
            Regex("""\bweed for sale|\bweed dealer|\bbuy weed"""),
            Regex("""\blsd\b|\becstasy\b|\bmoly\b|\bmdma\b"""),
            Regex("""\bfentanyl\b|\boxycontin\b|\bxanax for sale"""),
            Regex("""\bdrug deal|\bsel drugs|\bbuy drugs"""),
            Regex("""\bhow to (?:cook|make) meth"""),
            Regex("""\bshrooms?\s+for\s+sale|\bketamine\b|\bcocaine\s+dealer"""),
        ),

        ViolationType.CONTACT_HARVESTING to listOf(
            // Any mention of external communication platforms (standalone block)
            Regex("""\b(?:whatsapp|telegram|snapchat|instagram|tiktok|wechat|viber|discord|kik|skype|kakaotalk|zalo|line app|grindr|tinder|bumble|hinge|meetme|bereal|clubhouse)\b"""),
            // Other chat / call / meeting apps (and common misspellings)
            Regex("""\b(?:zangi|zangy|zanghi|zinger|zingi|botim|botem|threema|wickr|totok|bigo|bigo live|azar|hago|kik|justalk|wechat|we chat|qq|sharechat|clubhouse|omegle|chatroulette|hellotalk|hello talk|badoo|tantan|yubo|likee|kwai|triller|ometv|ome tv|camsurf|paltalk|ayoba|nimbuzz|hangouts|gmail|email me|e mail me)\b|\b(?:imo|signal|tango|yalla|hike|tandem|wink|soul|monkey|holla|line|wire|zoom|teams|meet|duo|skype) (?:app|id|account|number|link|call|meeting|chat)\b|\b(?:on|via|in|add me on|my|your|ur) (?:imo|signal|tango|zoom|teams|ms teams|google meet|google duo|kik)\b|\b(?:ms|microsoft) teams\b|\bgoogle (?:meet|duo)\b"""),
            // Short names and misspellings: insta, ig, snap, watsap, wtsp, tele, fb, tik tok…
            Regex("""\b(?:insta|instaa|instagram|instgram|instagrm|insta gram|instaid|ig|igram)\b"""),
            Regex("""\b(?:snap|snapchat|snp|snapp)\b"""),
            Regex("""\bw+h?a+t+s*\s*a+p+\b|\b(?:wtsp|wtsap|whtsap|watsp|wa number|whatsap)\b"""),
            Regex("""\b(?:tele|telgram|tlgrm|tg)\b"""),
            Regex("""\b(?:fb|face book|messenger|tik tok|tiktok|tt id)\b"""),
            Regex("""\b(?:my|your|ur) (?:num|numbr|no|contact|id|insta|snap|ig)\b"""),
            // Social media that can be used to redirect
            Regex("""\b(?:twitter|facebook|youtube|twitch|linkedin|pinterest|reddit|tumblr)\b"""),
            // DM / PM standalone or in any context
            Regex("""\b(?:dm|dms|pm|pms)\b"""),
            // "inbox me" / "direct message"
            Regex("""(?:inbox|direct\s+message|private\s+message)\s*me\b"""),
            // "hmu" / "hit me up"
            Regex("""\bhmu\b|hit\s+me\s+up"""),
            // "share your/my number/contact/fone"
            Regex("""share\s+(?:your|my|the)\s+(?:number|contact|fone|info|handle|username|profile|account)"""),
            Regex("""(?:give|get|send|drop)\s+(?:me\s+)?(?:your|my)\s+(?:number|contact|fone|info|handle)"""),
            // "call me" / "text me" / "ping me"
            Regex("""(?:call|text|ping|buzz)\s+me\b"""),
            // Moving the chat to another app / site
            Regex("""\b(?:another|other|different|diferent|second|some other|better|private) (?:app|apps|platform|website|social media|messenger|chat app|messaging app)\b"""),
            Regex("""\b(?:outside|out of|off|leave|beyond) (?:this|the|speaky) (?:app|aplication|application|platform|site|chat)\b"""),
            Regex("""\b(?:not|cant|can t) (?:talk|chat|speak) here\b|\btalk (?:there|over there)\b|\bnot (?:on|in) this app\b"""),
            Regex("""\b(?:which|what) (?:app|apps|social media|platform) (?:do )?(?:you|u) (?:use|have)\b"""),
            Regex("""\b(?:do you|u) (?:use|have) (?:any )?(?:social media|socials|other apps?)\b"""),
            // Generic redirect out of app
            Regex("""(?:lets?|let us)\s+(?:chat|talk|speak|meet|connect)\s+(?:outside|privately|in private|off(?:line)?|on another|somewhere else|elsewhere)"""),
            Regex("""(?:move|take this|continue)\s+(?:this\s+)?(?:conversation|chat|talk)\s+(?:to|outside|off|elsewhere)"""),
            Regex("""(?:contact|reach|message|find)\s+me\s+(?:outside|off|elsewhere|privately|directly)"""),
            // Spaced-out email evasion: "user at gmail dot com"
            Regex("""\w{2,}\s+(?:at|@)\s+\w{2,}\s+dot\s+(?:com|net|org|io|me)\b"""),
            Regex("""\w{2,}\s*\[at\]\s*\w{2,}\s*\[dot\]\s*\w{2,}"""),
            // "my username is" / "search me as"
            Regex("""(?:my\s+)?(?:username|handle|user\s+id|profile)\s+(?:is|:)\s+\w{3,}"""),
            Regex("""search\s+(?:me|my\s+name)\s+(?:on|in|at)\s+\w+"""),
            Regex("""(?:find|look\s+up)\s+me\s+on\s+\w+"""),
            // "follow me" anywhere
            Regex("""follow\s+me\b"""),
            // "add me" anywhere
            Regex("""add\s+me\b"""),
        ),

        ViolationType.SCAM to listOf(
            Regex("""send me (?:money|bitcoin|btc|eth|usdt|cash|funds)"""),
            Regex("""i (?:wil )?(?:send|give|transfer) you (?:money|bitcoin) if"""),
            Regex("""(?:crypto|bitcoin|btc|forex|investment) (?:signal|profit|doubl|tripl|100x|pump)"""),
            Regex("""(?:win|won|winner|congratulation).{0,30}(?:prize|reward|gift card)"""),
            Regex("""(?:click|tap) (?:here|this link) to (?:earn|win|get paid|invest)"""),
            Regex("""make \d+.{0,10}(?:per day|a day|daily|weekly|fast|easy)"""),
            Regex("""(?:paypal|cashapp|venmo|zele|western union)\s*(?:me|transfer|\$)"""),
            Regex("""(?:i\s+am\s+)?(?:stuck|stranded|trapped).{0,40}(?:send|need)\s+(?:money|help|funds)"""),
            Regex("""(?:pasive\s+income|work\s+from\s+home|easy\s+money|get\s+rich\s+fast)"""),
            Regex("""(?:mlm|pyramid\s+scheme|ponzi|referral\s+link)"""),
            Regex("""your (?:account|password|bank) (?:has been hacked|is compromised)"""),
            Regex("""(?:i\s+am\s+a\s+)?(?:prince|barrister|lawyer).{0,40}(?:inheritance|transfer|milion)"""),
            Regex("""(?:urgent|emergency).{0,20}(?:wire|transfer|send).{0,20}(?:money|\$\d+)"""),
            Regex("""gift\s+card\s+(?:scam|number|code|pin)"""),
        ),

        ViolationType.GAMBLING to listOf(
            Regex("""\bonline\s+(?:casino|poker|bet|gambling|slots)\b"""),
            Regex("""\bsports?\s+bet(?:ting)?\b"""),
            Regex("""\b(?:bet365|1xbet|draftkings|fanduel|bovada)\b"""),
            Regex("""\b(?:place\s+your\s+bets?|guaranteed\s+wins?|sure\s+(?:odds|tips?))\b"""),
        ),

        ViolationType.PROFANITY to listOf(
            Regex("""\bshit\b"""),
            Regex("""\bdamn\s+you|\bgo\s+to\s+hel\b"""),
            Regex("""\bmotherfucker|\bmofo\b"""),
            Regex("""\bpis\s+of\b|\bfuck\s+of\b"""),
            Regex("""\bwtf\b|\bstfu\b|\bgtfo\b"""),
            Regex("""\bdumbas|\bstupid\s+(?:idiot|moron|loser)"""),
            Regex("""\bbasard\b|\bpric\b|\bwanker\b"""),
        ),
    )
}
