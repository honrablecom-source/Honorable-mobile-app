#!/usr/bin/env python3
"""Generate the reviewed, deterministic Seran Benchmark V2 case manifest."""

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
assets = json.loads((ROOT / "assets.json").read_text())["assets"]
by_id = {a["id"]: a for a in assets}
cases = []


def add(query, expected=(), category="PHOTO SEMANTIC", difficulty="medium", moment=None):
    case = {
        "id": f"case-{len(cases)+1:03d}", "query": query, "expected": list(expected),
        "category": category, "difficulty": difficulty,
    }
    if not expected:
        case["no_match"] = True
    if moment:
        case["expected_timestamp"] = {"start": moment[0], "end": moment[1]}
    cases.append(case)


photo_queries = {
1:("the aerial photo of an orange tennis court","the tennis player casting a long shadow outside","SPORTS"),
2:("the woman sitting on the blue tennis court","the tennis photo where she had a yellow visor","MULTI-CONDITION"),
3:("the tennis racket and ball on a green court","the picture of a yellow ball beside a dark racket","OBJECT"),
4:("the sunrise over a tropical beach","the beach photo with sunlight across the ocean","SCENE"),
5:("the white sand beach with a palm tree","the beach picture with a sailboat and no people","NEGATIVE CONDITION"),
6:("the man wearing a plain white shirt","the picture where his shirt was white and his pants were dark","MULTI-CONDITION"),
7:("the folded blue jeans","the denim pants on a dark background","OBJECT"),
8:("the red running shoe on a red background","the red sneaker with a white sole","COLOR"),
9:("the gray running shoes outside","the close up of gray sneakers being worn","MULTI-CONDITION"),
10:("the bright office with white chairs","the modern lounge with chairs and hanging lights","INDOOR"),
11:("the empty office hallway","the office corridor with glass walls and no people","NEGATIVE CONDITION"),
12:("the dark restaurant with empty tables","the dining room with hanging lights but no customers","NEGATIVE CONDITION"),
13:("the snowy mountains under a purple night sky","the mountain picture with stars and no people","NEGATIVE CONDITION"),
14:("the woman wearing a blue denim jacket","the portrait where her dark hair was tied up","MULTI-CONDITION"),
15:("the man standing outside in a black jacket","the full body picture of a man outdoors","PEOPLE"),
16:("the close portrait of a woman with brown hair","the smiling woman against a plain background","PEOPLE"),
17:("the man with dark hair wearing a black shirt","the close portrait of a serious man","PEOPLE"),
18:("the woman wearing a white top","the portrait with the pale background and white clothing","COLOR"),
19:("the woman in a black top","the portrait of a woman with dark clothing","COLOR"),
20:("the woman outdoors with a blurry background","the close portrait outside in daylight","PEOPLE"),
21:("the smiling man in a blue shirt","the portrait with glasses and a blue top","MULTI-CONDITION"),
22:("the man wearing a suit outside","the formal portrait with a jacket and tie","CLOTHING"),
23:("the man in a plain gray shirt","the portrait with gray clothing and a neutral background","COLOR"),
24:("the colorful healthy salad bowl","the food photo with greens tomatoes and vegetables","FOOD"),
25:("the pizza topped with vegetables","the round pizza sitting on a table","FOOD"),
26:("the golden retriever outside","the large light colored dog in the grass","ANIMAL"),
27:("the small black pug","the dark dog looking toward the camera","ANIMAL"),
28:("the dense city skyline during the day","the city picture with many tall buildings","CITY"),
29:("the silver sports car outside","the modern gray car viewed from the front","CAR"),
30:("the red sports car on the road","the bright red car photographed outdoors","COLOR"),
31:("the yellow taxis on a city street","the traffic scene with several yellow cars","MULTI-CONDITION"),
32:("the green field beneath dramatic clouds","the grassy landscape with no buildings","NEGATIVE CONDITION"),
33:("the person standing in a wide mountain landscape","the outdoor picture with one tiny person","MULTI-CONDITION"),
34:("the sunlit mountain valley","the mountain photo with a person in the distance","MULTI-CONDITION"),
35:("the hiker overlooking the lake and mountains","the person standing above a blue mountain lake","ACTIVITY"),
36:("the dark city beneath the stars","the nighttime skyline with almost no daylight","NEGATIVE CONDITION"),
}
for number, (q1, q2, category) in photo_queries.items():
    path = by_id[f"photo-{number:03d}"]["path"]
    add(q1, [path], category, "easy")
    if number not in {34, 35, 36}:
        add(q2, [path], category, "hard")
add("the foggy lake surrounded by mountains and trees", [by_id["photo-037"]["path"]], "SCENE", "medium")

# Controlled OCR/color cases (12).
add("the screenshot that says payment successful", [by_id["photo-043"]["path"]], "OCR", "easy")
add("the receipt with ID HN 4821", [by_id["photo-043"]["path"]], "OCR", "hard")
add("the flight confirmation for AC 182", [by_id["photo-044"]["path"]], "OCR", "easy")
add("the screenshot mentioning Gate B14", [by_id["photo-044"]["path"]], "OCR", "medium")
add("the market receipt with a total of 42.80", [by_id["photo-045"]["path"]], "OCR", "easy")
add("the reminder to meet at 7:30 PM", [by_id["photo-046"]["path"]], "OCR", "easy")
add("the reminder for Community Hall", [by_id["photo-046"]["path"]], "OCR", "medium")
add("the payment screen showing a total paid of 86.40", [by_id["photo-043"]["path"]], "OCR", "medium")
add("the boarding information that says 6:45 PM", [by_id["photo-044"]["path"]], "OCR", "hard")
add("the receipt showing a subtotal of 38.50", [by_id["photo-045"]["path"]], "OCR", "medium")
add("the red and blue image with a white circle", [by_id["photo-047"]["path"]], "COLOR", "medium")
add("the green and yellow picture with a black circle", [by_id["photo-048"]["path"]], "COLOR", "medium")
add("the black and white image with a red circle", [by_id["photo-049"]["path"]], "COLOR", "medium")
add("the purple and orange picture with a green circle", [by_id["photo-050"]["path"]], "COLOR", "hard")
add("the color test that has orange but not blue", [by_id["photo-050"]["path"]], "NEGATIVE CONDITION", "hard")

video_queries = {
1:["the short park video with cars visible behind the trees","the five second clip moving through a green park","the park video where a white van is visible"],
2:["the park video passing a large dark tree trunk","the clip with a green fence beyond the trees","the park video with the biggest tree in the foreground"],
3:["the park video centered on a paved footpath","the clip moving straight down a path through trees","the green park video with a walkway in the middle"],
4:["the park video with traffic moving beyond the trees","the clip where trucks and cars pass on the road","the park scene looking across at a white building"],
5:["the park video with a winding paved path","the green park clip without traffic in the center","the thirty second walk through a leafy park"],
6:["the animated film with two men in a dark mechanical world","the video with strange machinery and an elevator","the dark animated movie with an older thin man"],
7:["the live action science fiction film with robots","the video with a man and woman near a canal","the movie where a large robot appears in a workshop"],
8:["the animated film about a red haired woman and a dragon","the fantasy video with snowy mountains and a cave","the movie where a woman fights a creature"],
9:["the animation reel with many unrelated scenes","the video montage containing a car and animated characters","the reel that shows an orange fruit exploding"],
10:["the rendering reel with a train and a coffee cup","the video montage showing a white miniature city","the reel with balloons rising over a city"],
}
for number, queries in video_queries.items():
    path = by_id[f"video-{number:03d}"]["path"]
    for index, query in enumerate(queries):
        add(query, [path], "VIDEO RETRIEVAL", "hard" if index else "medium")

durations = {i: by_id[f"video-{i:03d}"]["duration_ms"] / 1000 for i in range(1, 11)}


def moment(video, query, start_fraction, end_fraction, difficulty="hard"):
    duration = durations[video]
    add(query, [by_id[f"video-{video:03d}"]["path"]], "EXACT VIDEO MOMENT", difficulty,
        (round(duration * start_fraction, 3), round(duration * end_fraction, 3)))


moment(1, "the part of the short park clip where the white van passes", .00, 1.00)
moment(2, "the part where the camera is beside the huge tree trunk", .00, 1.00)
moment(3, "the moment the paved path is centered", .00, 1.00)
moment(4, "the part where traffic passes behind the park trees", .15, .90)
moment(5, "the part where the winding path fills the foreground", .00, 1.00)
moment(6, "the moment when two animated men stand on a narrow bridge", .08, .24)
moment(6, "the part where the thin older animated man is close to the camera", .48, .64)
moment(6, "the part near the end with orange light behind the characters", .72, .90)
moment(7, "the part where a man walks beside the Amsterdam canal", .20, .36)
moment(7, "the moment when the large robot is inside the workshop", .56, .72)
moment(7, "the part where the older man is shown outside", .74, .86)
moment(7, "the ending where blue robot graphics appear", .84, .96)
moment(8, "the moment when the dragon flies over the town", .22, .38)
moment(8, "the part where the woman crosses snowy mountains", .43, .60)
moment(8, "the dark cave scene with the creature", .58, .76)
moment(8, "the ending where the woman stands near white flowers", .82, .95)
moment(9, "the part where an orange fruit explodes on a blue background", .06, .18)
moment(9, "the moment showing the laboratory full of glass bottles", .16, .31)
moment(9, "the part with the white car driving between buildings", .43, .58)
moment(9, "the colorful cartoon creature inside a cave", .62, .78)
moment(10, "the part showing a close up of a computer keyboard", .06, .20)
moment(10, "the moment with the coffee cup on a wooden table", .46, .64)
moment(10, "the ending with balloons floating over a city", .78, .96)

for query in [
    "the photo of a birthday cake with candles", "the video where someone plays tennis",
    "the screenshot saying package delivered", "the picture of a cat sleeping on a sofa",
    "the video of people swimming in a pool", "the photo of a blue motorcycle in snow",
    "the receipt with a total of 99.99", "the beach photo with a red umbrella",
    "the video where a dog catches a frisbee", "the picture of sushi on a black plate",
    "the document mentioning passport number ZX 999", "the nighttime video of fireworks",
]:
    add(query, category="NO-MATCH", difficulty="hard")

assert len(cases) == 150, len(cases)
ordered = sorted(cases, key=lambda c: hashlib.sha256(c["id"].encode()).hexdigest())
holdout = {c["id"] for c in ordered[:45]}
for case in cases:
    case["split"] = "holdout" if case["id"] in holdout else "development"
(ROOT / "evaluation.json").write_text(json.dumps(cases, indent=2) + "\n")
print("cases=150 development=105 holdout=45")
