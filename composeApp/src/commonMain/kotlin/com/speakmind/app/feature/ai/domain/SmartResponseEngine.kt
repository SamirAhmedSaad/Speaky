package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.domain.model.Scenario

/**
 * Context-aware response engine that generates meaningful replies
 * based on the user's input and conversation context.
 * This is the interim solution until llama.cpp is integrated.
 */
object SmartResponseEngine {

    fun generateResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario?,
        userLevel: String,
    ): String {
        val lower = userMessage.lowercase().trim()

        // Greeting detection
        if (isGreeting(lower)) {
            return handleGreeting(lower)
        }

        // Question detection
        if (isQuestion(lower)) {
            return handleQuestion(lower, scenario)
        }

        // Opinion/feeling detection
        if (isAboutFeelings(lower)) {
            return handleFeelings(lower)
        }

        // Scenario-specific responses
        if (scenario != null) {
            return handleScenarioResponse(lower, scenario, userLevel)
        }

        // General contextual response
        return handleGeneral(lower, conversationHistory, userLevel)
    }

    private fun isGreeting(text: String): Boolean {
        val greetings = listOf("hello", "hi", "hey", "good morning", "good afternoon",
            "good evening", "how are you", "what's up", "howdy", "greetings", "nice to meet")
        return greetings.any { text.contains(it) }
    }

    private fun handleGreeting(text: String): String {
        return when {
            text.contains("how are you") ->
                "I'm doing great, thank you for asking! How about you? How has your day been so far?"
            text.contains("good morning") ->
                "Good morning! It's wonderful to start the day with some English practice. What would you like to talk about today?"
            text.contains("good evening") ->
                "Good evening! How was your day? I'd love to hear about it in English!"
            else ->
                "Hello! It's great to chat with you. What's on your mind today? We can talk about anything you'd like!"
        }
    }

    private fun isQuestion(text: String): Boolean {
        val questionStarters = listOf("what", "who", "where", "when", "why", "how", "which",
            "is", "are", "do", "does", "can", "could", "would", "will", "shall", "should")
        return text.endsWith("?") || questionStarters.any { text.startsWith(it) }
    }

    private fun handleQuestion(text: String, scenario: Scenario?): String {
        return when {
            // Animals
            text.contains("king") && (text.contains("animal") || text.contains("jungle") || text.contains("forest")) ->
                "The lion is often called the 'King of the Jungle'! It's a powerful animal that symbolizes strength and courage. Did you know that lions live in groups called 'prides'? Can you name any other animals that are considered symbols of power?"

            text.contains("biggest animal") || text.contains("largest animal") ->
                "The blue whale is the largest animal on Earth! It can grow up to 30 meters long. That's truly amazing, isn't it? What's your favorite animal?"

            text.contains("fastest animal") ->
                "The cheetah is the fastest land animal, reaching speeds up to 120 km/h! In the water, the sailfish is the fastest. Do you find animals fascinating?"

            // General knowledge
            text.contains("capital") && text.contains("france") ->
                "The capital of France is Paris! It's known as the 'City of Light'. Have you ever visited Paris or would you like to?"

            text.contains("capital") ->
                "That's a great geography question! Could you tell me which country's capital you're asking about? I'd love to help you practice English while learning about the world."

            // Language questions
            text.contains("mean") && (text.contains("what does") || text.contains("what do")) ->
                handleMeaningQuestion(text)

            text.contains("how do you say") || text.contains("how to say") ->
                "That's a great question! Could you tell me the word in your language? I'll help you find the right English expression and we can practice using it in a sentence."

            text.contains("difference between") ->
                handleDifferenceQuestion(text)

            // Weather
            text.contains("weather") ->
                "The weather is a classic conversation topic! In English, we often use it as small talk. For example: 'It's a lovely day, isn't it?' Tell me, what's the weather like where you are?"

            // Time
            text.contains("time") && (text.contains("what") || text.contains("tell")) ->
                "I can't check the time, but this is a great English practice moment! In English, we say 'What time is it?' or 'Could you tell me the time?' Can you think of other ways to ask about time?"

            // Age
            text.contains("how old") ->
                "As an AI tutor, I don't have an age, but I love this question because it's great English practice! The polite way to ask is 'How old are you?' or 'May I ask your age?' How old are you?"

            // Name
            text.contains("your name") || text.contains("who are you") ->
                "My name is Sage, and I'm your English tutor! I'm here to help you practice and improve your English through conversation. What's your name?"

            // Favorite things
            text.contains("favorite") || text.contains("favourite") ->
                handleFavoriteQuestion(text)

            // Yes/No questions
            text.startsWith("do you") || text.startsWith("can you") || text.startsWith("are you") ->
                handleYesNoQuestion(text)

            // Default question handler
            else ->
                "That's an interesting question! Let me think about that... " +
                generateContextualResponse(text) +
                " What made you curious about this?"
        }
    }

    private fun handleMeaningQuestion(text: String): String {
        val wordPattern = Regex("""what does? ["']?(\w+)["']? mean""")
        val word = wordPattern.find(text)?.groupValues?.getOrNull(1) ?: ""
        return if (word.isNotEmpty()) {
            "Great question about the word '$word'! Let me explain it simply: " +
            "To really learn a word, try using it in your own sentence. " +
            "Can you try making a sentence with '$word'?"
        } else {
            "I'd love to explain that word! Could you tell me which specific word or phrase you'd like me to explain?"
        }
    }

    private fun handleDifferenceQuestion(text: String): String {
        return when {
            text.contains("make") && text.contains("do") ->
                "'Make' is for creating something new (make a cake, make a decision), while 'do' is for actions and tasks (do homework, do the dishes). Can you make a sentence with each?"
            text.contains("say") && text.contains("tell") ->
                "'Say' is followed by the words spoken (She said 'hello'), while 'tell' needs a person (She told me about it). Try using both in a sentence!"
            text.contains("much") && text.contains("many") ->
                "'Much' is for uncountable things (much water, much time), and 'many' is for countable things (many books, many people). Which one would you use with 'friends'?"
            else ->
                "That's a great question about English! These words can be confusing. Let me explain the key difference: context and usage matter a lot in English. Can you give me a specific example of when you're confused about which to use?"
        }
    }

    private fun handleFavoriteQuestion(text: String): String {
        return when {
            text.contains("food") || text.contains("eat") ->
                "I think food vocabulary is fascinating! Some popular foods around the world include sushi, pizza, and tacos. What's your favorite food? Try describing it in English!"
            text.contains("movie") || text.contains("film") ->
                "Movies are a great way to learn English! Watching with English subtitles really helps. What's your favorite movie? Can you describe the plot in English?"
            text.contains("music") || text.contains("song") ->
                "Music is wonderful for learning English! Listening to English songs helps with pronunciation. What kind of music do you enjoy? Try telling me about your favorite artist!"
            text.contains("color") || text.contains("colour") ->
                "Colors are one of the first things we learn in a new language! My favorite is blue - it reminds me of the sky. What's your favorite color and why?"
            else ->
                "That's a fun question! Everyone has different favorites. What about you? Tell me about your favorite in a complete English sentence!"
        }
    }

    private fun handleYesNoQuestion(text: String): String {
        return when {
            text.contains("speak") && (text.contains("language") || text.contains("english")) ->
                "Yes, I communicate in English! I'm here to help you practice. The more we talk, the more fluent you'll become. What topic would you like to discuss?"
            text.contains("help") ->
                "Absolutely, I'd love to help you! That's exactly what I'm here for. Tell me what you need help with and we'll work on it together."
            text.contains("understand") ->
                "Yes, I can understand you! You're doing a great job expressing yourself in English. Keep going - what else would you like to talk about?"
            else ->
                "That's a great question! Yes, I'd be happy to discuss that further. Could you tell me more about what you're thinking?"
        }
    }

    private fun isAboutFeelings(text: String): Boolean {
        val feelingWords = listOf("feel", "happy", "sad", "tired", "excited", "nervous",
            "angry", "bored", "worried", "scared", "love", "hate", "enjoy")
        return feelingWords.any { text.contains(it) }
    }

    private fun handleFeelings(text: String): String {
        return when {
            text.contains("happy") || text.contains("great") || text.contains("excited") ->
                "That's wonderful to hear! Being happy is important. In English, there are many ways to express happiness: 'thrilled', 'delighted', 'over the moon'. Can you use one of these in a sentence?"
            text.contains("sad") || text.contains("upset") || text.contains("depressed") ->
                "I'm sorry to hear that. It's okay to feel that way sometimes. In English, we can say 'I'm feeling down' or 'I'm a bit blue'. Would you like to talk about what's bothering you? It's good practice!"
            text.contains("tired") || text.contains("exhausted") ->
                "Oh, that's too bad! 'Tired' has many synonyms in English: 'exhausted', 'worn out', 'drained', 'beat'. Try saying 'I'm worn out because...' and tell me why you're tired!"
            text.contains("bored") ->
                "Let's fix that! Conversations are the best cure for boredom. Here's a fun challenge: tell me about the most interesting thing that happened to you this week!"
            text.contains("love") ->
                "That's a strong feeling! In English, we use 'love' for many things: 'I love pizza', 'I love my family', 'I love traveling'. What do you love? Tell me in a complete sentence!"
            else ->
                "Thank you for sharing how you feel! Expressing emotions in English is an important skill. Can you describe your feeling in more detail? Try using 'because' to explain why."
        }
    }

    private fun handleScenarioResponse(text: String, scenario: Scenario, userLevel: String): String {
        val category = scenario.category.lowercase()
        return when {
            category.contains("restaurant") || category.contains("daily life") && scenario.title.lowercase().contains("order") ->
                "That sounds like a good choice! In a restaurant, you could also say 'I'd like to have...' or 'Could I get the...?' Would you like to see the dessert menu too?"
            category.contains("travel") || category.contains("airport") ->
                "Great question for a traveler! When you're at an airport or hotel, being polite goes a long way. Try using phrases like 'Excuse me, could you...' or 'Would you mind...?' What else do you need help with?"
            category.contains("work") || category.contains("business") ->
                "That's a professional response! In a business setting, remember to keep your language formal. Instead of 'want', say 'would like'. Can you rephrase your last sentence more formally?"
            category.contains("emergency") || category.contains("doctor") ->
                "It's important to communicate clearly in an emergency. Key phrases include 'I need help with...', 'I've been experiencing...', and 'How long will it take?' Can you practice describing your situation?"
            else ->
                generateContextualResponse(text) + " What else would you like to discuss about ${scenario.title.lowercase()}?"
        }
    }

    private fun handleGeneral(text: String, history: List<ChatMessage>, userLevel: String): String {
        // Look at conversation history for context
        val lastAiMessage = history.lastOrNull { it.role == MessageRole.ASSISTANT }?.content ?: ""

        // Short responses need encouragement
        if (text.split(" ").size <= 3) {
            return "I appreciate your answer! Could you try to expand on that? " +
                   "In English, longer sentences help you practice more. " +
                   "Try starting with 'I think that...' or 'In my opinion...' and tell me more!"
        }

        return generateContextualResponse(text) +
               " That's a really good point. Can you tell me more about that?"
    }

    private fun generateContextualResponse(text: String): String {
        // Extract key nouns/topics from the text for a more relevant response
        val topics = extractTopics(text)

        return when {
            topics.contains("school") || topics.contains("study") || topics.contains("learn") ->
                "Education is so important! Learning new things keeps our minds sharp."
            topics.contains("work") || topics.contains("job") || topics.contains("office") ->
                "Work life can be challenging but rewarding. Talking about your job in English is great practice!"
            topics.contains("family") || topics.contains("mother") || topics.contains("father") || topics.contains("parents") ->
                "Family is such a meaningful topic! It's wonderful that you want to talk about them."
            topics.contains("travel") || topics.contains("trip") || topics.contains("vacation") ->
                "Traveling is one of the best ways to practice English in real life!"
            topics.contains("food") || topics.contains("cook") || topics.contains("eat") || topics.contains("restaurant") ->
                "Food is a universal language! Describing flavors and dishes is excellent vocabulary practice."
            topics.contains("sport") || topics.contains("play") || topics.contains("game") || topics.contains("football") ->
                "Sports bring people together! Talking about games and matches is fun English practice."
            topics.contains("music") || topics.contains("song") || topics.contains("sing") ->
                "Music really helps with language learning! Songs are great for pronunciation practice."
            topics.contains("book") || topics.contains("read") || topics.contains("story") ->
                "Reading is one of the best ways to improve your English vocabulary naturally!"
            topics.contains("movie") || topics.contains("film") || topics.contains("watch") ->
                "Watching movies in English is fantastic practice for listening skills!"
            topics.contains("friend") || topics.contains("people") || topics.contains("social") ->
                "Having conversations with friends is the best way to improve fluency!"
            else ->
                "That's really interesting! I can see you're thinking carefully about what to say."
        }
    }

    private fun extractTopics(text: String): Set<String> {
        val words = text.lowercase().split(Regex("[\\s,;.!?]+"))
        val stopWords = setOf("i", "a", "an", "the", "is", "am", "are", "was", "were", "be",
            "been", "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "for", "on", "with",
            "at", "by", "from", "it", "this", "that", "and", "or", "but", "not", "my", "your",
            "me", "you", "he", "she", "we", "they", "very", "really", "just", "also", "so",
            "about", "like", "think", "know", "want", "get", "go", "some", "what", "when")
        return words.filter { it.length > 2 && it !in stopWords }.toSet()
    }
}
