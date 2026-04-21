#!/usr/bin/env python3
"""Apply combined 'current — suggested' meanings to vocabulary.json for 98 flagged words."""

import json
from pathlib import Path

VOCAB_FILE = (
    Path(__file__).parent.parent
    / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
)

# Format: word -> suggested addition (appended after " — ")
SUGGESTIONS = {
    # A1 — vague / circular
    "airport":      "a transportation hub where passengers board and depart on flights",
    "baseball":     "a bat-and-ball sport where teams take turns hitting and fielding",
    "basket":       "a woven or wire container used to hold or carry things",
    "bathroom":     "a room used for washing and personal hygiene",
    "begin":        "to take the first step in doing something",
    "church":       "a place where a Christian community gathers to pray and worship together",
    "coffee":       "a popular caffeinated drink brewed from ground roasted beans",
    "cook":         "to prepare food by applying heat to raw ingredients",
    "cross":        "to go from one side to the other; a mark or symbol shaped like a plus sign",
    "drink":        "to take liquid into the mouth and swallow it",
    "evening":      "the hours between late afternoon and bedtime",
    "everybody":    "used to refer to all people in a group or situation",
    "friendly":     "showing warmth and goodwill toward other people",
    "homework":     "tasks assigned by a teacher to be completed outside the classroom",
    "known":        "widely recognized or understood by many people",
    "let":          "to give permission for something to happen",
    "light":        "the natural brightness that allows us to see; something that produces brightness",
    "lucky":        "experiencing good outcomes by chance rather than effort",
    "mountain":     "a very high, steep landmass that rises above the surrounding area",
    "number":       "a word or symbol representing a count or quantity",
    "orange":       "a round juicy citrus fruit with bright orange peel",
    "painting":     "a picture created by applying colored paint to a surface",
    "paper":        "thin flat sheets used for writing, printing, or wrapping",
    "photo":        "a captured image of a moment, taken with a camera",
    "pool":         "a contained body of water for swimming or gathering",
    "shut":         "to move a door, lid, or opening so that it is closed",
    "singer":       "someone who performs songs using their voice",
    "study":        "to read and learn about a subject carefully and with attention",
    "tell":         "to communicate facts, stories, or instructions to someone",
    "think":        "to use your mind to form ideas, opinions, or solutions",
    "throw":        "to send an object through the air by moving your arm",
    "turn":         "to move so as to face a different direction; a chance to do something",
    # A2 — vague / circular
    "achieve":      "to successfully reach a goal through effort and determination",
    "chemical":     "a substance with a defined composition, used in or produced by reactions",
    "community":    "a group of people sharing the same place, interests, or background",
    "consider":     "to think carefully about something before making a decision",
    "create":       "to bring something new into existence through skill or imagination",
    "discover":     "to find or learn something for the first time",
    "discuss":      "to exchange ideas or opinions about a topic with others",
    "electronic":   "operated by or involving small electrical components and circuits",
    "everyone":     "all people in a group or all people in general",
    "financial":    "connected to the management of money, investments, or banking",
    "fishing":      "the activity of trying to catch fish for sport or food",
    "form":         "the shape or structure of something; an official document to fill in",
    "handle":       "to hold and use with your hands; to deal with a situation",
    "medical":      "connected to the science and practice of treating illness and injury",
    "method":       "a planned, step-by-step way of doing or achieving something",
    "mix":          "to blend two or more things together into one",
    "newspaper":    "a daily or weekly publication reporting current news and events",
    "occur":        "to take place or come about, often unexpectedly",
    "officer":      "a person who holds an official position of authority or rank",
    "personal":     "belonging to or affecting one individual in particular",
    "proper":       "meeting the accepted standard; right for the situation",
    "properly":     "in the right or appropriate way",
    "recently":     "in the time period just before now",
    "remain":       "to continue to be in the same place or state",
    "require":      "to make something necessary; to officially demand something",
    "seriously":    "in a way that shows genuine concern or importance",
    "service":      "work done for others or for the public; a ceremony or organized event",
    "simply":       "in an easy or uncomplicated way; used for emphasis",
    "talk":         "to express thoughts and ideas using spoken words",
    "teaching":     "the work of helping others learn through explanation and guidance",
    "web":          "a spider's silky trap; the global network of internet pages",
    "writing":      "the activity of recording language on a surface; written text or literature",
    # B1 — circular / vague
    "championship": "a competition held to decide who is the best player or team",
    "childhood":    "the early period of a person's life, from birth to adolescence",
    "conflict":     "a serious disagreement, struggle, or fight between people or groups",
    "differ":       "to be unlike something else; to hold a contrasting opinion",
    "eastern":      "situated in or facing the east; relating to Asia or eastern cultures",
    "elsewhere":    "in or to a different place from the one already mentioned",
    "friendship":   "a close relationship between people built on trust and shared experience",
    "historian":    "a scholar who researches, records, and interprets past events",
    "inform":       "to give someone knowledge or facts about a topic",
    "judgment":     "a conclusion or opinion formed after thought; a legal decision",
    "leader":       "someone who guides and inspires a group toward a shared goal",
    "lonely":       "unhappy because of lacking companions or meaningful connection",
    "mostly":       "to the greatest extent; in the majority of cases",
    "obtain":       "to gain something through effort, request, or purchase",
    "permit":       "to give official approval or consent for something",
    "personality":  "the set of traits and behaviors that make someone who they are",
    "personally":   "done by the individual themselves; from a personal point of view",
    "repair":       "to restore something broken or damaged back to working condition",
    "reply":        "to say or write something in response to what someone said",
    "retain":       "to continue holding, owning, or remembering something",
    "signature":    "a person's name written by hand, used to authorize or identify",
    "somewhat":     "to a moderate or limited degree",
    "speaker":      "someone who addresses an audience; a device that produces sound",
    "tap":          "to touch or strike something gently with a quick, light motion",
    "terribly":     "to a very great degree; in an extremely bad or serious way",
    "weekly":       "happening or produced once every seven days",
    "writer":       "someone whose job or skill is producing written content",
    # B2
    "management":   "the process of organizing and overseeing people or resources",
    "political":    "relating to the government, policies, or exercise of power",
    "reality":      "the state of things as they actually exist, not as imagined",
    # C1
    "landscape":    "an expanse of land with its natural or designed scenery",
    "leadership":   "the skill and position of guiding others toward a shared purpose",
    "realistic":    "based on what is actually possible or likely in real life",
    "specialist":   "an expert with deep knowledge in a specific field or subject",
}


def main():
    print(f"Loading: {VOCAB_FILE}")
    with open(VOCAB_FILE, encoding="utf-8") as f:
        data = json.load(f)

    updated = 0
    not_found = []

    for level in data["levels"]:
        for entry in level["words"]:
            word = entry["word"]
            if word not in SUGGESTIONS:
                continue
            current = entry.get("meaning", "").strip()
            suggestion = SUGGESTIONS[word]
            entry["meaning"] = f"{current} — {suggestion}"
            updated += 1
            print(f"  {word:<20} → {entry['meaning'][:80]}")

    for w in SUGGESTIONS:
        matched = any(
            e["word"] == w
            for lvl in data["levels"]
            for e in lvl["words"]
        )
        if not matched:
            not_found.append(w)

    print(f"\nUpdated: {updated} / {len(SUGGESTIONS)}")
    if not_found:
        print(f"Not found in vocab: {not_found}")

    with open(VOCAB_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Saved: {VOCAB_FILE}")


if __name__ == "__main__":
    main()
