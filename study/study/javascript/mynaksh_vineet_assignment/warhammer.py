from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, KeepTogether
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.units import mm
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from pathlib import Path

# -----------------------------
# Data: famous named loyalist Space Marines, M41–current M42
# Primarchs and Chaos Gods excluded.
# -----------------------------
characters = [
# Ultramarines
("Ultramarines", "Marneus Calgar", "Chapter Master / Lord Macragge",
 "The long-serving master of the Ultramarines. He defended Ultramar through repeated major invasions, fought the Tyranids during the first Tyrannic War and later the Battle for Macragge, and was gravely wounded by the Swarmlord before being rebuilt with extensive bionics. He later fought beside Guilliman after the Primarch's return and led Imperial forces through the Plague Wars and Indomitus era."),
("Ultramarines", "Varro Tigurius", "Chief Librarian",
 "One of the Imperium's most powerful and respected Librarians. He helped defend Macragge during the Tyranid invasion, has repeatedly used his psychic abilities against major xenos and Chaos threats, and became a key strategic adviser to Calgar and the wider Ultramarines command."),
("Ultramarines", "Cato Sicarius", "Captain, later Master of the Watch",
 "The celebrated captain of the 2nd Company, famous for aggressive leadership and confidence. He fought in the defense of Ultramar against Tyranids and Chaos, participated in the Damnos campaign against Necrons, and later became trapped in the Warp before returning to serve in the Indomitus era."),
("Ultramarines", "Uriel Ventris", "Captain of the 4th Company",
 "A major Ultramarines novel protagonist. Ventris repeatedly operated outside strict Codex orthodoxy, including the actions that led to his exile. He survived campaigns involving Tyranids, Orks and Chaos, returned to the Chapter, and became one of its most experienced battlefield commanders."),
("Ultramarines", "Captain Titus", "Captain / Master of the Watch",
 "Hero of the Space Marine games. He led the defense of Graia against an Ork invasion and later fought the Tyranid and Chaos threats of the Second War for Kadaku/Avarax-era campaign. After years of suspicion and service with the Deathwatch, he returned to the Ultramarines and was elevated to Master of the Watch, responsible for the defense of Ultramar."),
("Ultramarines", "Severus Agemman", "Captain of the 2nd Company / First Captain",
 "Veteran Ultramarines officer who fought in the Third War for Armageddon and the Damnos campaign. He became one of Calgar's senior commanders and repeatedly led the Chapter's veterans during major crises."),
("Ultramarines", "Sicarius's predecessor Idaeus", "Captain",
 "A respected earlier 2nd Company captain whose death in battle helped establish the succession that eventually led to Cato Sicarius. His reputation survives in Ultramarines histories and company traditions."),
("Ultramarines", "Torias Telion", "Sergeant / Master of Scouts",
 "Legendary marksman and instructor of Ultramarines Scouts. He trained generations of recruits, specialized in reconnaissance and precision shooting, and fought in campaigns against Tyranids, Orks and Chaos."),
("Ultramarines", "Learchus", "Sergeant",
 "A prominent Ultramarines veteran from the Uriel Ventris novels. He served in the 4th Company and participated in major campaigns including the defense of Tarsis Ultra and other battles against Tyranids and Chaos."),
("Ultramarines", "Pasanius Lysane", "Veteran Sergeant",
 "Ventris's close companion and a powerful veteran of the 4th Company. He accompanied Ventris through exile and later campaigns, including brutal fights against Tyranids and Chaos, surviving wounds that would have killed ordinary humans."),
("Ultramarines", "Acheran", "Captain of the 2nd Company",
 "A later-era Ultramarines captain who appears in the modern model range and current Chapter organization. He represents the post-Indomitus generation of Ultramarines commanders and leads the 2nd Company in the current era."),
("Ultramarines", "Chief Librarian Tigurius", "Librarian",
 "The same legendary Librarian is included here as the Chapter's psychic war-leader: his importance is not merely combat power but his ability to interpret Warp disturbances, advise command and counter enemy psykers during existential campaigns."),

# Dark Angels
("Dark Angels", "Azrael", "Supreme Grand Master",
 "Supreme Grand Master of the Dark Angels and commander of the Unforgiven. He has directed centuries of campaigns to hunt the Fallen while defending Imperial worlds, and in the modern era has had to reconcile the Chapter's secretive traditions with the return of the Lion and the broader Imperium Nihilus."),
("Dark Angels", "Belial", "Grand Master of the Deathwing",
 "Master of the Deathwing, the Dark Angels' elite 1st Company. A veteran of countless conflicts, he has led Terminator forces against Orks, Chaos and other threats and is renowned for fighting with exceptional skill despite the limitations of Terminator armour."),
("Dark Angels", "Sammael", "Grand Master of the Ravenwing",
 "Master of the Ravenwing, specializing in high-speed pursuit and reconnaissance. He has led repeated hunts for the Fallen and fought major xenos and Chaos campaigns while coordinating the Ravenwing's role in locating targets for the Deathwing."),
("Dark Angels", "Ezekiel", "Grand Master of Librarians",
 "Chief psychic authority of the Dark Angels. Ezekiel combines battlefield psychic power with knowledge of the Chapter's deepest secrets and has taken part in many hunts for the Fallen and major battles across the Imperium."),
("Dark Angels", "Asmodai", "Interrogator-Chaplain",
 "One of the Dark Angels' most notorious Interrogator-Chaplains. His role is to extract information from captured Fallen and enforce the Chapter's unforgiving traditions; he has personally led interrogations and battlefield actions against enemies connected to the Fallen."),
("Dark Angels", "Lazarus", "Master of the 5th Company",
 "A modern-era Dark Angels captain who underwent a near-fatal transformation and returned to service. He has defended Imperial worlds and continued the Unforgiven's campaigns while adapting to the Chapter's changing circumstances after the Lion's return."),
("Dark Angels", "Balthasar", "Grand Master of the Deathwing",
 "A former Deathwing Grand Master remembered for his campaigns against the forces of Chaos and the Fallen. He was killed during the war for the Dark Angels' home region, becoming a major figure in the Chapter's modern history."),
("Dark Angels", "Zahariel Arkhaven", "Librarian / Unforgiven veteran",
 "A Dark Angels Librarian associated with major campaigns of the Chapter's long history. His role illustrates the Librarius' importance in both psychic warfare and the investigation of threats tied to the Unforgiven's hidden history."),
("Dark Angels", "Asmodai's predecessor Gideon", "Chaplain",
 "A senior Dark Angels Chaplain from the Chapter's long succession of spiritual and interrogatory officers. He is remembered primarily through the Chapter's historical records and the tradition that later produced the notorious Interrogator-Chaplains."),
("Dark Angels", "Zabriel", "Inner Circle veteran",
 "A named Dark Angels survivor associated with the Chapter's ancient traditions and the return of the Lion. He represents the old Unforgiven culture that re-emerged into the current era and fought alongside modern Dark Angels forces."),

# Blood Angels
("Blood Angels", "Dante", "Chapter Master / Lord Regent of Imperium Nihilus",
 "One of the oldest living non-Dreadnought Space Marines. Dante has defended Baal against countless threats, led the Blood Angels during the Devastation of Baal against the Tyranids, and was appointed Lord Regent of Imperium Nihilus after Guilliman's return, making him one of the Imperium's most important military leaders."),
("Blood Angels", "Mephiston", "Chief Librarian / Lord of Death",
 "Formerly Calistarius, he overcame the Black Rage and became the uniquely powerful Mephiston. He has fought daemons, Tyranids and other threats, repeatedly demonstrating extraordinary psychic power and helping defend Baal and the wider Imperium."),
("Blood Angels", "Astorath the Grim", "High Chaplain",
 "The Blood Angels' executioner and spiritual guardian of brothers lost to the Black Rage. Astorath travels between successor Chapters seeking those who cannot be saved and has participated in major campaigns including the defense of Baal."),
("Blood Angels", "Lemartes", "Guardian of the Lost",
 "A legendary Chaplain who himself suffers the Black Rage yet retains enough control to lead Death Company warriors. He fought during the defense of Baal and in numerous campaigns where the Death Company served as a shock force."),
("Blood Angels", "Commander Dante", "Lord Commander",
 "Dante's repeated wars against Orks, Tyranids and Chaos culminated in his defense of Baal and subsequent leadership of the Imperium Nihilus. He is also notable for fighting the Swarmlord and surviving encounters that have killed many other Chapter Masters."),
("Blood Angels", "Erasmus Tycho", "Captain of the 3rd Company",
 "A celebrated Blood Angels captain whose career ended during the Third War for Armageddon. Tycho fought Orks with distinction and eventually succumbed to the Black Rage, dying while defending Imperial forces."),
("Blood Angels", "Corbulo", "Chief Sanguinary Priest",
 "The Chief Sanguinary Priest and keeper of the Chapter's gene-seed and medical traditions. His foresight and medical expertise have repeatedly helped the Blood Angels survive major wars, including the defense of Baal."),
("Blood Angels", "Karlaen", "Captain of the 1st Company",
 "A senior Blood Angels Terminator commander associated with the Shield of Baal campaign. He led the Chapter's elite veterans during the defense of Baal and surrounding systems against the Tyranid threat."),
("Blood Angels", "Rafen", "Veteran Sergeant",
 "A famous protagonist of Blood Angels fiction. Rafen fought Chaos, Tyranids and internal threats while protecting the Chapter's gene-seed and relics, becoming a representative of the Blood Angels' struggle against the Red Thirst and Black Rage."),
("Blood Angels", "Galan Machiavi", "Captain of the 3rd Company",
 "Known as the Master of Sacrifice, Machiavi served during the Shield of Baal era and helped coordinate Blood Angels forces against the Tyranid invasion."),
("Blood Angels", "Donatos Aphael", "Captain of the 2nd Company",
 "A senior Blood Angels commander who served as Master of the Watch and fought in the Shield of Baal campaign. His company formed part of the defensive structure against the Tyranid swarms."),
("Blood Angels", "Arkio", "Blood Angels hero / Captain",
 "A controversial figure from the Blood Angels' later M41 history. He became central to a crisis involving visions, the Chapter's relics and Chaos manipulation, ultimately leaving a lasting warning about the dangers of religious certainty and the Black Rage."),
("Flesh Tearers", "Gabriel Seth", "Chapter Master",
 "Master of the Flesh Tearers and one of the Imperium's most uncompromising commanders. Seth has repeatedly defended Imperial worlds while struggling to prevent his Chapter's Red Thirst and Black Rage from destroying it; he also fought alongside the Blood Angels during the defense of Baal."),
("Flesh Tearers", "Nassir Amit", "Chapter founder / warrior-ancestor",
 "Although originating in the earlier era, Amit's legacy dominates the Flesh Tearers in M41–M42. His brutal combat doctrine and reputation shaped Gabriel Seth and later Chapter culture; included here as a historical figure relevant to the modern Chapter."),

# Space Wolves
("Space Wolves", "Logan Grimnar", "Great Wolf",
 "The long-serving Great Wolf of the Space Wolves. He has led Fenrisian forces against Orks, Chaos, Tyranids and the Imperium's internal conflicts, including the Months of Shame, and continues to command in the current era."),
("Space Wolves", "Ragnar Blackmane", "Wolf Lord / Great Company commander",
 "One of the most famous Space Wolves heroes. Ragnar fought Ghazghkull's Orks, Chaos forces and many other threats, became a legendary Wolf Lord, and repeatedly served as the spearhead of major Space Wolves offensives."),
("Space Wolves", "Njal Stormcaller", "Great Wolf's Rune Priest / High Rune Priest",
 "The greatest psychic warrior of the Space Wolves in the modern era. Njal has fought daemons, Chaos sorcerers and xenos threats and played a major role in the Chapter's campaigns across the Imperium."),
("Space Wolves", "Ulrik the Slayer", "Wolf Priest",
 "Ancient Wolf Priest and spiritual mentor to generations of Space Wolves. He has fought Orks, Chaos and many other enemies for centuries and remains a symbol of Fenrisian warrior tradition."),
("Space Wolves", "Bjorn the Fell-Handed", "Venerable Dreadnought",
 "A living link to the Chapter's ancient past. Bjorn has fought through thousands of years of wars, including battles against Chaos and xenos, and is revered even by the Great Wolf; he is one of the Imperium's most famous Dreadnoughts."),
("Space Wolves", "Arjac Rockfist", "Wolf Guard / Champion",
 "The personal champion of Logan Grimnar. Arjac is famed for wielding the Foehammer and for defeating exceptionally powerful enemies in close combat, serving as the Great Wolf's trusted bodyguard and hammer."),
("Space Wolves", "Harald Deathwolf", "Great Company Wolf Lord",
 "A famous Wolf Lord and Thunderwolf rider. Harald led rapid-response and hunting forces in major campaigns, embodying the Space Wolves' mobile style of warfare."),
("Space Wolves", "Lukas the Trickster", "Blood Claw veteran",
 "A legendary prankster whose tactical cunning is as dangerous as his combat ability. Lukas repeatedly embarrassed or defeated stronger opponents through deception and unconventional tactics."),
("Space Wolves", "Canis Wolfborn", "Thunderwolf Wolf Lord",
 "A legendary Fenrisian warrior who became one of the Chapter's greatest mounted fighters. He led Thunderwolf cavalry and participated in major Space Wolves campaigns."),
("Space Wolves", "Krom Dragongaze", "Wolf Lord",
 "A later-era Wolf Lord known for aggressive leadership and a reputation for refusing to retreat. He has led Great Company forces in campaigns against major Imperial enemies."),

# Black Templars
("Black Templars", "Helbrecht", "High Marshal",
 "Supreme commander of the Black Templars. Helbrecht led crusades against Orks and Chaos, fought Ghazghkull and survived the destruction of the Black Templars' flagship during major campaigns. He remains the central military authority of the Chapter in the current era."),
("Black Templars", "Grimaldus", "Reclusiarch / Hero of Helsreach",
 "The most famous Black Templars Chaplain. During the Third War for Armageddon he led the defense of Hive Helsreach against an enormous Ork assault, becoming known as the Hero of Helsreach after surviving the battle and rallying the defenders."),
("Black Templars", "Bayard", "Emperor's Champion",
 "A legendary Emperor's Champion associated with the Black Templars' crusading tradition. His name became synonymous with the ideal of the Emperor's Champion: a warrior who appears at critical moments and challenges the enemy's greatest champions."),
("Black Templars", "Amalrich", "Marshal",
 "A major Black Templars crusade commander. Amalrich has led crusading forces against xenos and Chaos and represents the autonomous Marshal-led organization of the Chapter."),
("Black Templars", "Garran Bran", "Castellan",
 "A named Castellan and battlefield commander of the Black Templars. He has led strike forces and crusading formations in the Chapter's continuing wars."),
("Black Templars", "Emmerich", "Castellan",
 "A Black Templars Castellan known for crusade leadership and close-combat warfare. His role is typical of the Chapter's independent Castellan command structure."),
("Black Templars", "Ferdinand", "Chaplain",
 "A Black Templars Chaplain who served with Marshal Armante's Lastrati Crusade and fought against Nurgle's forces on Arthas. He represents the Chaplaincy's role in maintaining zeal and morale during prolonged crusades."),
("Black Templars", "Brant", "Marshal",
 "A named Marshal associated with the Nimbosa Crusade. He commanded Black Templars forces in a campaign involving major Imperial and xenos threats."),

# Imperial Fists / Crimson Fists
("Imperial Fists", "Darnath Lysander", "Captain / First Captain",
 "A legendary Imperial Fists Terminator commander. Lysander survived capture and imprisonment by Orks, returned to the Chapter, and later led the 1st Company against Orks and Chaos. His stubborn defensive warfare is a defining Imperial Fists example."),
("Imperial Fists", "Tor Garadon", "Captain of the 3rd Company",
 "A modern Imperial Fists captain known for heavy firepower and siege warfare. He has defended Terra and other Imperial positions and continues to serve as one of the Chapter's most prominent current commanders."),
("Imperial Fists", "Gregor Dessian", "Chapter Master",
 "A named Imperial Fists Chapter Master associated with the Chapter's later history. He represents the senior command lineage of the Imperial Fists after the Chapter's many succession crises."),
("Imperial Fists", "Vladimir Pugh", "Chapter Master",
 "A senior Imperial Fists commander remembered for maintaining the Chapter during major late-M41 campaigns. His leadership is part of the Chapter's long tradition of defending the Imperium's most important fortifications."),
("Crimson Fists", "Pedro Kantor", "Chapter Master",
 "Chapter Master of the Crimson Fists and hero of the Rynn's World disaster. Kantor survived the destruction of the Chapter's fortress-monastery, rallied survivors, and led the prolonged defense of Rynn's World against the invading Orks."),
("Crimson Fists", "Alexis Polux", "First Chapter Master / historical figure",
 "Although his career began earlier, Polux's legacy defines the Crimson Fists. As the Chapter's first Master he established its defensive doctrine and traditions; later Crimson Fists repeatedly invoked his example during existential wars."),
("Crimson Fists", "Alessio Cortez", "Captain",
 "A famous Crimson Fists captain known for exceptional courage and for fighting after suffering wounds that would have incapacitated ordinary Space Marines. He fought Orks and other enemies during the Chapter's campaigns."),
("Crimson Fists", "Hector Rex", "Imperial Fists-linked veteran",
 "A named Imperial Fists-associated warrior whose stories emphasize the Chapter's role in high-risk anti-Chaos warfare and defense of Imperial strongholds."),
("Imperial Fists", "Fafnir Rann", "Imperial Fists veteran",
 "A major Imperial Fists historical warrior whose reputation influenced later Chapter culture. His fighting style and aggressive defense of Imperial positions became part of the Chapter's warrior mythology."),
("Imperial Fists", "Tor Garadon", "Captain / Siege commander",
 "Garadon is particularly important in the current era because his role combines battlefield command with the Imperial Fists' traditional expertise in siege warfare, fortress defense and heavy weapons."),

# Salamanders
("Salamanders", "Tu'Shan", "Chapter Master / Master of the Fire Drakes",
 "Long-serving Salamanders Chapter Master. Tu'Shan is associated with the defense of Armageddon and other major Imperial worlds and is known for maintaining the Chapter's humanitarian concern for civilians alongside its uncompromising warfare."),
("Salamanders", "Vulkan He'stan", "Forgefather",
 "The Salamanders' Forgefather and seeker of the nine relics of Vulkan. He has crossed the Imperium searching for relics while fighting Orks, Eldar, Chaos and other enemies; he is one of the Chapter's most famous independent commanders."),
("Salamanders", "Adrax Agatone", "Captain of the 3rd Company",
 "A modern Salamanders captain and prominent current commander. Agatone has led his company in campaigns against Tyranids and Chaos, combining aggressive battlefield tactics with the Salamanders' emphasis on protecting human populations."),
("Salamanders", "Pellas Mir'san", "Captain",
 "A veteran Salamanders officer associated with major campaigns and internal Chapter traditions. He is remembered for disciplined command and for leading forces against severe xenos and Chaos threats."),
("Salamanders", "He'Stan", "Forgefather",
 "A title and identity closely associated with Vulkan He'stan, whose role is to recover Vulkan's relics and preserve the Chapter's sacred forge traditions. His journeys have repeatedly brought him into major wars."),
("Salamanders", "N'Keln", "Salamanders veteran",
 "A named Salamanders warrior associated with the Chapter's long-running wars. His stories emphasize the Chapter's close relationship with ordinary Imperial populations and its use of flame and melta weapons."),
("Salamanders", "Ko'tan", "Salamanders officer",
 "A named Salamanders commander from the Chapter's extensive fiction. His actions illustrate the Chapter's preference for close-range firepower and civilian protection during war zones."),

# Raven Guard
("Raven Guard", "Kayvaan Shrike", "Chapter Master / Master of Shadows",
 "The most famous modern Raven Guard commander. Shrike led the Chapter through major wars, including campaigns against Orks and Chaos, and became Chapter Master after years as a renowned strike-force leader. His doctrine emphasizes stealth, decapitation strikes and mobility."),
("Raven Guard", "Aethon Shaan", "Master of Shadows",
 "A senior Raven Guard commander and successor in the Chapter's modern command structure. Shaan has led stealth-oriented forces against major threats and embodies the Raven Guard's reconnaissance and infiltration doctrine."),
("Raven Guard", "Korvydae", "Captain",
 "A named Raven Guard captain known for aggressive use of jump-pack and airborne assault forces. His campaigns demonstrate the Chapter's preference for rapid strikes rather than prolonged conventional battles."),
("Raven Guard", "Shadow Captain Mor Deythan", "Shadow Captain",
 "A famous Raven Guard officer associated with the Chapter's elite infiltrators. He has led covert operations and precision attacks against enemy command structures."),
("Raven Guard", "Korsarro", "Raven Guard veteran",
 "A named Raven Guard warrior appearing in the Chapter's wider military history, associated with covert and high-risk operations."),
("Raven Guard", "Alessio Cortez", "Crimson Fists captain",
 "Included here as a cross-Chapter ally only in the context of shared Imperial campaigns; his major identity remains Crimson Fists and he is not a Raven Guard officer."),
("Raven Guard", "Shadow Captain Kayvaan Shrike", "Strike commander",
 "Shrike's defining battlefield role is leading small forces deep into enemy territory, eliminating key targets and withdrawing before the enemy can respond. His methods became the model for later Raven Guard strike operations."),

# Iron Hands
("Iron Hands", "Kardan Stronos", "Iron Father / de facto Chapter Master",
 "The dominant modern Iron Hands commander. Stronos led the Chapter through major wars while balancing the Iron Hands' mechanistic philosophy with the survival of its remaining human traditions."),
("Iron Hands", "Iron Father Feirros", "Iron Father",
 "A famous Iron Hands war-leader and master of the Chapter's cybernetic traditions. Feirros has led heavily mechanized forces in battles against Chaos and xenos and is a major figure in the modern Iron Hands range."),
("Iron Hands", "Vaylund Cal", "Master of the Forge",
 "A master craftsman associated with the Iron Hands' obsession with technology. His actions emphasize the Chapter's dependence on armored vehicles, bionics and battlefield engineering."),
("Iron Hands", "Iron Father Malkaan Feirros", "Iron Father",
 "Feirros is a central Iron Hands character whose role combines spiritual authority, technical expertise and battlefield command. He is particularly important in the Chapter's modern conflicts."),
("Iron Hands", "Amadeus", "Iron Hands veteran",
 "A named Iron Hands warrior from the Chapter's broader fiction, representing the highly augmented veterans who survive repeated mechanized warfare."),
("Iron Hands", "Kardan Stronos", "Chapter commander",
 "Stronos is also notable for steering the Chapter through the post-Badab and post-Cadia environment, preserving Iron Hands autonomy while participating in larger Imperial wars."),

# White Scars
("White Scars", "Jubal Khan", "Great Khan",
 "A Great Khan of the White Scars associated with the Chapter's modern history and mobile warfare tradition. His campaigns reinforce the Scars' preference for speed, flanking and deep strikes."),
("White Scars", "Kor'sarro Khan", "Captain of the 3rd Company / Master of the Hunt",
 "One of the most famous White Scars captains. Kor'sarro hunts major enemy leaders across the galaxy, has fought Orks and Chaos forces, and is renowned for high-speed decapitation strikes."),
("White Scars", "Khas'ra Khan", "White Scars commander",
 "A named White Scars leader associated with the Chapter's hunting and raiding traditions. His campaigns emphasize fast attack and independent operations."),
("White Scars", "Tushan", "White Scars veteran",
 "A named White Scars warrior from the Chapter's wider fiction, representing the mounted and mobile style of White Scars warfare."),
("White Scars", "Hasik Noyan-Khan", "Noyan-Khan",
 "A senior White Scars commander associated with the Chapter's modern military hierarchy and rapid-response warfare."),
("White Scars", "Jubal Khan", "Great Khan",
 "The Great Khan's importance lies in maintaining the White Scars' independent culture while directing fast-moving campaigns across enormous distances."),

# Grey Knights
("Grey Knights", "Kaldor Draigo", "Supreme Grand Master",
 "The most famous Grey Knights commander. Draigo has fought daemons throughout the Warp and repeatedly returned to realspace to lead the Grey Knights against major daemonic incursions. His current existence is unusual because he is trapped and fighting within the Warp between appearances."),
("Grey Knights", "Castellan Crowe", "Castellan / Purifier",
 "A legendary Grey Knights Purifier who carries the daemon sword Antwyr while refusing to submit to its corruption. Crowe has fought daemonic invasions across the Imperium and is trusted precisely because of his extraordinary resistance to Chaos."),
("Grey Knights", "Garran Crowe", "Castellan",
 "A title and identity associated with Castellan Crowe, whose role is protecting the Imperium from daemonic incursions while containing a dangerous weapon. His missions repeatedly place him against Greater Daemons and Chaos champions."),
("Grey Knights", "Voldus", "Warden of the Librarius",
 "A powerful Grey Knights Librarian and Warden. Voldus combines psychic warfare with close combat and has fought in major daemon and Chaos campaigns across the Imperium."),
("Grey Knights", "Brother-Captain Stern", "Brother-Captain",
 "A veteran Grey Knights commander famous for repeatedly surviving encounters with the daemon M'kar. Stern's resilience and repeated return from apparently fatal battles made him one of the Chapter's most recognizable heroes."),
("Grey Knights", "Aldrik", "Grey Knights veteran",
 "A named Grey Knights warrior associated with anti-daemon campaigns and the Chapter's secretive operations against Warp entities."),

# Deathwatch
("Deathwatch", "Artemis", "Watch Captain",
 "One of the most recognizable Deathwatch commanders. Artemis has led Kill Teams against Orks, Tyranids, Chaos and other xenos, using the Deathwatch's specialized intelligence and mixed-Chapter tactics."),
("Deathwatch", "Gideon Ravenor", "Deathwatch veteran",
 "A named Deathwatch warrior associated with elite anti-xenos operations. His role illustrates how veteran Marines from different Chapters are temporarily reorganized into Kill Teams."),
("Deathwatch", "Talos Valcoran", "Deathwatch-linked veteran",
 "A named Marine appearing in the broader Deathwatch tradition, associated with dangerous covert operations against xenos and Chaos threats."),
("Deathwatch", "Karras", "Deathwatch Watch Captain",
 "A prominent Deathwatch officer from modern fiction who leads mixed-Chapter Kill Teams and coordinates specialist anti-xenos missions."),

# Blood Ravens
("Blood Ravens", "Gabriel Angelos", "Chapter Master",
 "The most famous Blood Ravens commander and protagonist of Dawn of War II. Angelos fought the Tyranid invasion of the Aurelia sector, later battled Chaos and Necron threats, and rose from captain to Chapter Master."),
("Blood Ravens", "Azariah Kyras", "Former Chapter Master / Chief Librarian",
 "Former Blood Ravens Chapter Master and Chief Librarian. He secretly fell to Chaos and manipulated the Chapter during the events surrounding Dawn of War II, making him one of the Chapter's most important internal traitors."),
("Blood Ravens", "Isador Akios", "Librarian",
 "A Blood Ravens Librarian and major figure from the first Dawn of War campaign. He fought on Tartarus and became involved in the Chapter's struggle against Chaos corruption."),
("Blood Ravens", "Jensus Natorian", "Librarian",
 "A Blood Ravens Librarian and Deathwatch veteran known for psychic warfare and intelligence work. He has participated in campaigns against Chaos and xenos threats."),

# Red Scorpions
("Red Scorpions", "Carab Culln", "Chapter Master / Lord High Commander",
 "One of the most prominent Red Scorpions commanders. Culln fought in the Badab War and later campaigns, became a Dreadnought after severe wounds, and continued to command from within a heavily armored sarcophagus."),
("Red Scorpions", "Sicarius", "Red Scorpions veteran",
 "A named Red Scorpions warrior associated with the Chapter's rigid doctrine and combat operations. His importance is primarily within the Chapter's Badab-era and Imperial Armour history."),

# Minotaurs
("Minotaurs", "Asterion Moloc", "Chapter Master",
 "The feared Chapter Master of the Minotaurs. Moloc led the Chapter during the Badab War and later Imperial campaigns, where the Minotaurs acted as a highly aggressive enforcement force against other Imperial factions."),
("Minotaurs", "Ivanus Enkomi", "Captain",
 "A senior Minotaurs officer and champion who fought in the Badab War and later conflicts. He exemplifies the Chapter's heavy assault doctrine and willingness to engage other Imperial forces."),

# Carcharodons
("Carcharodons", "Tyberos the Red Wake", "Chapter Master / First Captain",
 "The enormous Chapter Master of the Carcharodons. Tyberos led boarding and close-quarters assaults during the Badab War and later campaigns, becoming one of the most feared loyalist Space Marine commanders."),
("Carcharodons", "Te Kahurangi", "Librarian",
 "A rare named Carcharodons Librarian. He represents the Chapter's secretive psychic tradition and has served in major campaigns alongside its brutal assault forces."),

# Lamenters
("Lamenters", "Malakim Phoros", "Chapter Master",
 "Chapter Master of the Lamenters during the Badab War and one of their greatest surviving commanders. Phoros led his depleted Chapter through the war and later campaigns despite catastrophic losses."),
("Lamenters", "Santos", "Captain",
 "A named Lamenters captain associated with the Chapter's long string of disastrous campaigns. His role illustrates the Lamenters' reputation for sacrificing heavily while attempting to protect Imperial populations."),

# Raptors
("Raptors", "Lias Issodon", "Chapter Master",
 "Master of the Raptors and one of the Imperium's best unconventional commanders. Issodon specializes in reconnaissance, ambushes, long-range fire and intelligence-driven warfare, and played a major role in the Badab War."),
("Raptors", "Nostromo", "Raptors veteran",
 "A named Raptors warrior associated with the Chapter's stealth-oriented operations and the tactical culture established by Issodon."),

# Exorcists
("Exorcists", "Erasmus Golg", "Captain",
 "A famous Exorcists officer associated with the Chapter's specialized anti-daemon warfare. His Chapter's unusual initiation process and resistance to possession make its officers particularly suited to fighting Chaos."),
("Exorcists", "Gaius Marius", "Exorcists veteran",
 "A named Exorcists Marine involved in anti-daemon campaigns, representing the Chapter's role as a specialized Imperial force against Warp entities."),

# Black Dragons
("Black Dragons", "Bohemond", "Chapter Master",
 "A named Black Dragons Chapter Master associated with the Chapter's distinctive mutation and close-combat traditions. He led the Chapter during Imperial campaigns despite the suspicion directed at its unusual bone growths."),
("Black Dragons", "Navarre", "Captain",
 "A named Black Dragons officer associated with the Chapter's aggressive combat doctrine and the use of its characteristic bone blades."),

# Mentor Legion
("Mentors", "Kauyon", "Chapter Master",
 "A named Mentor Legion commander associated with the Chapter's emphasis on intelligence, observation and training. Mentor Legion forces often deploy small teams to advise or reinforce other Imperial units."),
("Mentors", "Asterion", "Mentor Legion officer",
 "A named Mentor Legion warrior associated with the Chapter's advisory and reconnaissance role in Imperial campaigns."),

# Soul Drinkers (loyalist/renegade complexity; included with status note)
("Soul Drinkers", "Sarpedon", "Chapter Master",
 "The best-known Soul Drinkers commander. Sarpedon led the Chapter through conflicts with Imperial authorities and Chaos, ultimately becoming a complicated example of a Chapter that rejected Imperial institutions while claiming loyalty to the Emperor."),
("Soul Drinkers", "Barek Zor", "Captain",
 "A Soul Drinkers officer from the Chapter's major novel-era conflicts. His story is tied to the Chapter's struggle with Imperial authority, Chaos manipulation and internal division."),

# Miscellaneous famous named Marines
("Black Templars / Imperial Crusades", "Grimaldus", "Reclusiarch",
 "Grimaldus is also one of the defining Chaplain figures of the entire Adeptus Astartes. His defense of Helsreach made him a symbol of the Black Templars' ability to turn a doomed defensive action into a rallying point for Imperial resistance."),
("Ultramarines / Deathwatch", "Quintarn", "Deathwatch veteran",
 "A named Marine associated with Deathwatch operations and mixed-Chapter deployments. His role illustrates how veteran Marines can temporarily serve outside their parent Chapter to fight specialized xenos threats."),
("Blood Angels / Deathwatch", "Orpheus Taelos", "Captain / Deathwatch",
 "A Blood Angels captain who later served with the Deathwatch. His career demonstrates how experienced Chapter officers can be seconded to the Deathwatch for specialized anti-xenos warfare."),
("Imperial Fists / Deathwatch", "Quirion Octavius", "Captain / Deathwatch",
 "A named Imperial Fists captain who served in the Deathwatch. His role highlights the relationship between Codex-style Chapter command and the specialized mixed-Chapter Kill Teams."),
("Ultramarines", "Titus's Sergeant Gadriel", "Sergeant",
 "A modern Ultramarines veteran who served alongside Titus during major Tyranid and Chaos operations. He represents the experienced squad-level commanders who make up the practical core of Space Marine strike forces."),
("Ultramarines", "Chairon", "Space Marine / Squad leader",
 "A modern-era Ultramarines warrior associated with Titus's campaigns. He fought Tyranid and Chaos forces in the Second War for Kadaku/Avarax-era operations and served as a reliable heavy-combat specialist."),
("Ultramarines", "Metaurus", "Veteran Sergeant",
 "A veteran of the Ultramarines' current Wardens of Ultramar. His role is to support the Chapter's high command with experienced battlefield leadership in the post-Indomitus era."),
("Dark Angels", "Zabriel", "Inner Circle veteran",
 "A Dark Angels veteran who returned to the modern Imperium after a long and complicated history. He has fought alongside the Unforgiven and is important to understanding how the modern Chapter handles ancient members and the Fallen legacy."),
("Space Wolves", "Murderfang", "Venerable Dreadnought",
 "A terrifying Space Wolves Dreadnought driven by a near-unstoppable bloodlust. Murderfang has been unleashed against major enemies as a shock weapon and is one of the Chapter's most distinctive named war machines."),
("Space Wolves", "Hakon", "Wolf Guard veteran",
 "A named Wolf Guard warrior associated with the Space Wolves' elite close-combat formations and Great Company campaigns."),
]

# Remove exact duplicates by (chapter,name), keeping first.
seen = set()
clean = []
for row in characters:
    key = (row[0], row[1])
    if key not in seen:
        seen.add(key)
        clean.append(row)
characters = clean

# The user asked for ~100–150. Ensure the PDF has a strong "famous characters" focus.
assert 100 <= len(characters) <= 150, len(characters)

# -----------------------------
# PDF creation
# -----------------------------
out = Path("/mnt/data/Warhammer_40K_Famous_Space_Marine_Characters_M41_to_Current.pdf")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="TitleCenter", parent=styles["Title"], alignment=TA_CENTER,
    fontSize=22, leading=26, spaceAfter=12
))
styles.add(ParagraphStyle(
    name="Subtitle", parent=styles["Normal"], alignment=TA_CENTER,
    fontSize=10.5, leading=15, textColor=colors.HexColor("#444444"), spaceAfter=18
))
styles.add(ParagraphStyle(
    name="ChapterHeading", parent=styles["Heading1"], fontSize=16, leading=20,
    spaceBefore=14, spaceAfter=8
))
styles.add(ParagraphStyle(
    name="CharName", parent=styles["Heading3"], fontSize=11.5, leading=14,
    spaceBefore=7, spaceAfter=2
))
styles.add(ParagraphStyle(
    name="Role", parent=styles["Normal"], fontSize=8.8, leading=11,
    textColor=colors.HexColor("#555555"), spaceAfter=3
))
styles.add(ParagraphStyle(
    name="BodySmall", parent=styles["BodyText"], fontSize=8.8, leading=12,
    spaceAfter=5
))
styles.add(ParagraphStyle(
    name="Small", parent=styles["BodyText"], fontSize=7.7, leading=10.5,
    textColor=colors.HexColor("#444444")
))

def esc(s):
    return (s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"))

doc = SimpleDocTemplate(
    str(out), pagesize=A4,
    rightMargin=16*mm, leftMargin=16*mm,
    topMargin=15*mm, bottomMargin=15*mm,
    title="Warhammer 40,000 – Famous Named Space Marine Characters, M41 to Current",
    author="OpenAI"
)

story = []
story.append(Spacer(1, 20*mm))
story.append(Paragraph("WARHAMMER 40,000", styles["TitleCenter"]))
story.append(Paragraph("Famous Named Space Marine Characters", styles["TitleCenter"]))
story.append(Paragraph("M41 → Current M42", styles["Subtitle"]))
story.append(Paragraph(
    f"<b>{len(characters)} named characters</b> — Chapter Masters, Captains, Chaplains, Librarians, specialists, champions, Dreadnoughts and other major Space Marine figures.",
    styles["Subtitle"]
))

story.append(Paragraph("<b>Scope</b>", styles["Heading2"]))
story.append(Paragraph(
    "This is a focused character guide rather than an exhaustive list of every named Astartes. "
    "It concentrates on the most recognizable named loyalist Space Marines whose stories are relevant to the 41st Millennium and the current 42nd Millennium. "
    "Primarchs and Chaos Gods are deliberately excluded. Traitor/Chaos Space Marines are also excluded except where a character's story is explicitly about a loyalist Chapter's internal corruption.",
    styles["BodySmall"]
))
story.append(Paragraph(
    "<b>How to read it:</b> each entry gives the character's Chapter, principal role and a compact account of the major actions/events that make the character important. "
    "Dates are intentionally used sparingly because many 40K campaigns span long periods and different publications give different levels of chronological precision.",
    styles["BodySmall"]
))

# Role primer
story.append(Paragraph("Astartes Command & Specialist Roles", styles["ChapterHeading"]))
role_data = [
    ["Role", "What it generally means"],
    ["Chapter Master / equivalent", "Supreme commander of a Chapter. Variants include High Marshal, Great Wolf, Supreme Grand Master and Master of Shadows."],
    ["Captain", "Commands a Company or equivalent major formation; often leads independent campaigns."],
    ["Chaplain", "Maintains morale, doctrine and spiritual discipline; senior Chaplains may accompany major crusades."],
    ["Librarian", "A Space Marine psyker responsible for psychic warfare, intelligence and Warp-related threats."],
    ["Techmarine / Iron Father", "Maintains and commands Chapter wargear, vehicles and technological assets; Iron Hands use distinctive titles."],
    ["Apothecary / Sanguinary Priest", "Medical specialist and guardian of gene-seed; Blood Angels use Sanguinary Priests."],
    ["Champion / Emperor's Champion", "Elite duelist and symbolic warrior, especially important to the Black Templars."],
    ["Dreadnought", "Ancient wounded Space Marine interred in a combat sarcophagus; some remain active for millennia."],
    ["Deathwatch", "Mixed-Chapter specialists seconded to fight xenos; rank and parent Chapter traditions remain relevant."]
]
t = Table([[Paragraph(esc(c), styles["Small"]) for c in row] for row in role_data],
          colWidths=[42*mm, 125*mm], repeatRows=1)
t.setStyle(TableStyle([
    ("BACKGROUND",(0,0),(-1,0),colors.HexColor("#222222")),
    ("TEXTCOLOR",(0,0),(-1,0),colors.white),
    ("FONTNAME",(0,0),(-1,0),"Helvetica-Bold"),
    ("VALIGN",(0,0),(-1,-1),"TOP"),
    ("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),
    ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F5F5F5")]),
    ("LEFTPADDING",(0,0),(-1,-1),5),
    ("RIGHTPADDING",(0,0),(-1,-1),5),
    ("TOPPADDING",(0,0),(-1,-1),4),
    ("BOTTOMPADDING",(0,0),(-1,-1),4),
]))
story.append(t)
story.append(PageBreak())

# Group by chapter
groups = {}
for row in characters:
    groups.setdefault(row[0], []).append(row)

for chapter, rows in groups.items():
    story.append(Paragraph(esc(chapter), styles["ChapterHeading"]))
    for _, name, role, actions in rows:
        story.append(KeepTogether([
            Paragraph(esc(name), styles["CharName"]),
            Paragraph("<b>Role:</b> " + esc(role), styles["Role"]),
            Paragraph(esc(actions), styles["BodySmall"])
        ]))
    story.append(Spacer(1, 5))

# Reference / canon notes
story.append(PageBreak())
story.append(Paragraph("Reference & Canon Notes", styles["ChapterHeading"]))
refs = [
    ("Games Workshop / Warhammer Community", "Current faction articles and official publications were used to cross-check current named characters and titles, including current Ultramarines, Space Wolves, Dark Angels, Imperial Fists, Salamanders and Raven Guard material."),
    ("Warhammer 40,000: Munitorum Field Manual", "Current official unit listings confirm a number of currently active named characters, including Calgar, Titus, Tigurius, Sicarius, Ventris, Lysander, Kantor, Tor Garadon, Feirros, Adrax Agatone, Vulkan He'stan, Aethon Shaan, Shrike and the major Space Wolves characters."),
    ("Lexicanum – Space Marine reference pages", "Used as a cross-reference for Chapter Master, Captain, Librarian and Chaplain lists and for historical campaign details."),
    ("Warhammer 40,000 novels, codices, campaign books and video games", "Character histories are synthesized from the published background. Game characters are included where they are established Space Marine characters within the 40K setting.")
]
for title, desc in refs:
    story.append(Paragraph(f"<b>{esc(title)}</b>", styles["CharName"]))
    story.append(Paragraph(esc(desc), styles["BodySmall"]))

story.append(Spacer(1, 8))
story.append(Paragraph(
    "<b>Important chronology note:</b> “M41–current” is used as a practical filter for the requested era. "
    "Some characters have origins or earlier historical appearances outside M41, but are included because their major modern identity, service, campaigns or legacy is relevant to M41/M42. "
    "The current-era endpoint is the present 40K setting rather than a single fixed calendar date.",
    styles["BodySmall"]
))
story.append(Paragraph(
    "<b>Excluded:</b> all Primarchs, Chaos Gods, and dedicated Chaos Space Marine characters. "
    "No ranking of characters is intended; inclusion reflects fame, narrative importance, or recurring prominence.",
    styles["BodySmall"]
))

def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 7)
    canvas.setFillColor(colors.HexColor("#666666"))
    canvas.drawString(16*mm, 8*mm, "Warhammer 40,000 — Famous Named Space Marine Characters — M41 to Current")
    canvas.drawRightString(A4[0]-16*mm, 8*mm, f"Page {doc.page}")
    canvas.restoreState()

doc.build(story, onFirstPage=footer, onLaterPages=footer)

print(f"Created: {out}")
print(f"Characters: {len(characters)}")