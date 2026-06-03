package com.speakmind.app.feature.community.data

enum class ViolationType(val userMessage: String) {
    SEXUAL("Sexual or explicit content is not allowed in this community."),
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
        // Phase 1: raw text checks (emails, URLs, @handles — must run before normalization
        // because normalization strips @ and dots that are needed for pattern matching)
        for ((type, patterns) in RAW_RULES) {
            if (patterns.any { it.containsMatchIn(text) }) {
                return ModerationResult(isAllowed = false, violationType = type)
            }
        }
        // Phase 2: normalized text checks (leet-speak, obfuscated content)
        val lower = normalize(text)
        for ((type, patterns) in NORMALIZED_RULES) {
            if (patterns.any { it.containsMatchIn(lower) }) {
                return ModerationResult(isAllowed = false, violationType = type)
            }
        }
        return ModerationResult(isAllowed = true)
    }

    // Normalize leet-speak and obfuscation tricks
    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00AD]"), "") // zero-width / soft-hyphen
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

        ViolationType.SEXUAL to listOf(
            Regex("""fuck|fuk|fck|fuq"""),
            Regex("""sex(?:y|ting|ual|cam)?"""),
            Regex("""porn|porno|xxx|hentai|nsfw"""),
            Regex("""nude|naked|nudes"""),
            Regex("""dick|cock|penis|shaft|boner"""),
            Regex("""pussy|vagina|cunt|vulva"""),
            Regex("""boob|tit|breast|nipple|areola"""),
            Regex("""asshole|anus|butthole"""),
            Regex("""dildo|vibrat|masturbat"""),
            Regex("""orgasm|cumshot|ejaculat"""),
            Regex("""blowjob|handjob|rimjob|fellatio|cunnilingus"""),
            Regex("""rape|rapist|molest"""),
            Regex("""horny|erection"""),
            Regex("""onlyfans|camgirl|stripper|escort|prostitut|hookup"""),
            Regex("""erotic|one night stand"""),
            Regex("""bitch|whore|slut"""),
            Regex("""pedophil|child porn|loli"""),
            Regex("""incest|bestiality"""),
        ),

        ViolationType.HATE_SPEECH to listOf(
            Regex("""\bniger|\bniga\b"""),      // post-normalize (double letters collapsed)
            Regex("""\bfagot|\bfag\b"""),
            Regex("""\bkike\b|\bjew pig|\bjew rat"""),
            Regex("""\bspic\b|\bwetback\b"""),
            Regex("""\bchink\b|\bgok\b|\bslant\b"""),
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
            Regex("""rope method|jump from"""),
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
            Regex("""\b(?:whatsapp|telegram|snapchat|instagram|tiktok|wechat|signal|viber|discord|kik|skype|kakaotalk|imo|zalo|line app|grindr|tinder|bumble|hinge|meetme|bereal|clubhouse)\b"""),
            // Social media that can be used to redirect
            Regex("""\b(?:twitter|facebook|youtube|twitch|linkedin|pinterest|reddit|tumblr)\b"""),
            // DM / PM standalone or in any context
            Regex("""\b(?:dm|dms|pm|pms)\b"""),
            // "inbox me" / "direct message"
            Regex("""(?:inbox|direct\s+message|private\s+message)\s*me\b"""),
            // "hmu" / "hit me up"
            Regex("""\bhmu\b|hit\s+me\s+up"""),
            // "share your/my number/contact/phone"
            Regex("""share\s+(?:your|my|the)\s+(?:number|contact|phone|info|handle|username|profile|account)"""),
            Regex("""(?:give|get|send|drop)\s+(?:me\s+)?(?:your|my)\s+(?:number|contact|phone|info|handle)"""),
            // "call me" / "text me" / "ping me"
            Regex("""(?:call|text|ping|buzz)\s+me\b"""),
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
