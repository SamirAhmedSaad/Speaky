package com.speakmind.app.feature.community.ui.room

/** A Hub conversation topic: tapping it puts one of its [questions] in the message box. */
data class HubTopic(val emoji: String, val name: String, val questions: List<String>)

/** Easy, friendly conversation starters (about B1), all allowed by the chat filter. */
val HUB_TOPICS = listOf(
    HubTopic(
        "🍔", "Food",
        listOf(
            "What is your favourite food from your country?",
            "Do you like cooking? What can you cook well?",
            "What did you eat for breakfast today?",
            "Which food from another country do you love?",
            "Sweet or salty snacks, which do you prefer and why?",
            "What is a traditional dish everyone should try?",
            "Do you prefer eating at home or in restaurants?",
            "What food did you hate as a child but like now?",
        ),
    ),
    HubTopic(
        "✈️", "Travel",
        listOf(
            "Which country would you love to visit and why?",
            "What is the best trip you have ever had?",
            "Do you prefer the beach or the mountains?",
            "What should a tourist see first in your city?",
            "Have you ever been lost in a new place? What happened?",
            "Do you like travelling alone or with others?",
            "What is one thing you always pack for a trip?",
            "Which place in your country is the most beautiful?",
        ),
    ),
    HubTopic(
        "🎬", "Movies",
        listOf(
            "What is the best movie you watched this year?",
            "Do you watch movies with English subtitles?",
            "Which TV series do you recommend for learning English?",
            "Who is your favourite actor and why?",
            "Do you prefer comedies, action films or documentaries?",
            "What movie can you watch again and again?",
            "Cinema or home, where do you like watching films?",
            "Which cartoon did you love as a child?",
        ),
    ),
    HubTopic(
        "📚", "Learning",
        listOf(
            "How long have you been learning English?",
            "What is the hardest part of English for you?",
            "Which English word do you always forget?",
            "How do you practise speaking every day?",
            "What is your best tip for learning new words?",
            "Do you think grammar or vocabulary is more important?",
            "Which English accent do you like the most?",
            "What is one English mistake you used to make?",
        ),
    ),
    HubTopic(
        "💼", "Work & study",
        listOf(
            "What do you do, work or study?",
            "What is your dream job and why?",
            "What do you like most about your job or studies?",
            "Do you prefer working from home or in an office?",
            "What skill would you like to learn this year?",
            "What was your favourite subject at school?",
            "How do you stay focused when you study?",
            "Would you like to work in another country one day?",
        ),
    ),
    HubTopic(
        "⚽", "Sports",
        listOf(
            "Which sport do you like to watch or play?",
            "Who is the best football player right now?",
            "Do you exercise every week? What do you do?",
            "Which team do you support?",
            "Have you ever tried a new sport? How was it?",
            "Walking, running or cycling, which do you prefer?",
            "What sport is popular in your country?",
            "Do you prefer team sports or individual sports?",
        ),
    ),
    HubTopic(
        "🎵", "Music",
        listOf(
            "What kind of music do you listen to?",
            "Which English song helped you learn English?",
            "Can you play a musical instrument?",
            "Who is your favourite singer or band?",
            "Do you listen to music while you study or work?",
            "What song always makes you happy?",
            "What traditional music is famous in your country?",
            "Do you prefer old songs or new songs?",
        ),
    ),
    HubTopic(
        "🌍", "Culture",
        listOf(
            "What is a special celebration in your country?",
            "What surprised you about another culture?",
            "How do people greet each other in your country?",
            "What is a famous saying in your language?",
            "Which holiday is your favourite and why?",
            "What do visitors usually find strange in your country?",
            "What is something you are proud of in your culture?",
            "How is your city different from ten years ago?",
        ),
    ),
    HubTopic(
        "🎯", "Goals",
        listOf(
            "What is one goal you have for this month?",
            "What would you do with one extra hour every day?",
            "What habit do you want to start this year?",
            "Where do you see yourself in five years?",
            "What is something new you learned this week?",
            "What motivates you to keep learning?",
            "What is the best advice you have ever received?",
            "If you could master any skill instantly, what would it be?",
        ),
    ),
    HubTopic(
        "😂", "Fun",
        listOf(
            "Would you rather be able to fly or be invisible?",
            "What is the funniest thing that happened to you this week?",
            "If you were an animal, which one would you be?",
            "What superpower would you choose and why?",
            "Tea or coffee? Explain your choice!",
            "If you could have dinner with anyone, who would it be?",
            "What is a small thing that makes your day better?",
            "Morning person or night person?",
        ),
    ),
    HubTopic(
        "📱", "Technology",
        listOf(
            "What is your favourite way to learn online?",
            "Could you live one week without your phone?",
            "What do you think about AI helping us learn?",
            "What technology do you think will change the world next?",
            "Do you prefer reading on paper or on a screen?",
            "How much time do you spend on your phone each day?",
            "What was your first phone like?",
            "Which gadget would you like to buy next?",
        ),
    ),
)
