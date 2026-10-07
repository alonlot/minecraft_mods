package com.hogwartsmagic.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class HogwartsGuideScreen extends Screen {
    private static final int PAGE_WIDTH = 420;
    private static final int PAGE_HEIGHT = 248;

    private Section currentSection = Section.WELCOME;
    private int currentPage;
    private int left;
    private int top;

    public HogwartsGuideScreen() {
        super(Component.translatable("gui.hogwartsmagic.hogwarts_guide"));
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - PAGE_WIDTH) / 2;
        this.top = (this.height - PAGE_HEIGHT) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearWidgets();

        int tabX = this.left - 126;
        int tabY = this.top + 16;
        for (Section section : Section.values()) {
            Section target = section;
            this.addRenderableWidget(Button.builder(section.label(), button -> {
                this.currentSection = target;
                this.currentPage = 0;
                rebuildButtons();
            }).bounds(tabX, tabY, 112, 20).build());
            tabY += 24;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.previous"), button -> {
            if (this.currentPage > 0) {
                this.currentPage--;
            }
        }).bounds(this.left + 18, this.top + PAGE_HEIGHT - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.next"), button -> {
            if (this.currentPage < this.currentSection.pages().size() - 1) {
                this.currentPage++;
            }
        }).bounds(this.left + PAGE_WIDTH - 88, this.top + PAGE_HEIGHT - 28, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.close"), button -> onClose())
                .bounds(this.left + PAGE_WIDTH - 88, this.top + 12, 70, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, 0xB0100906);

        guiGraphics.fill(this.left - 2, this.top - 2, this.left + PAGE_WIDTH + 2, this.top + PAGE_HEIGHT + 2, 0xFF6A4A25);
        guiGraphics.fill(this.left, this.top, this.left + PAGE_WIDTH, this.top + PAGE_HEIGHT, 0xFFF3E2BE);
        guiGraphics.fill(this.left, this.top, this.left + PAGE_WIDTH, this.top + 4, 0xFFFFF5DE);
        guiGraphics.fill(this.left, this.top + PAGE_HEIGHT - 4, this.left + PAGE_WIDTH, this.top + PAGE_HEIGHT, 0xFFD4B07B);
        guiGraphics.fill(this.left + PAGE_WIDTH / 2 - 1, this.top + 8, this.left + PAGE_WIDTH / 2 + 1, this.top + PAGE_HEIGHT - 36, 0x44926C3B);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.hogwartsmagic.hogwarts_guide.header"), this.left + PAGE_WIDTH / 2, this.top + 14, 0x5A3420);
        guiGraphics.drawCenteredString(this.font, this.currentSection.label(), this.left + PAGE_WIDTH / 2, this.top + 30, 0x6D4725);

        List<Page> pages = this.currentSection.pages();
        this.currentPage = Mth.clamp(this.currentPage, 0, Math.max(0, pages.size() - 1));
        Page page = pages.get(this.currentPage);

        int textLeft = this.left + 20;
        int textTop = this.top + 52;
        int textWidth = PAGE_WIDTH - 40;

        guiGraphics.drawString(this.font, page.title().copy().withStyle(ChatFormatting.BOLD), textLeft, textTop, 0x402616, false);
        int cursorY = textTop + 16;
        for (Component line : page.lines()) {
            for (var split : this.font.split(line, textWidth)) {
                guiGraphics.drawString(this.font, split, textLeft, cursorY, 0x3A2416, false);
                cursorY += this.font.lineHeight + 1;
            }
            cursorY += 5;
        }

        guiGraphics.drawCenteredString(this.font, Component.literal((this.currentPage + 1) + " / " + pages.size()), this.left + PAGE_WIDTH / 2, this.top + PAGE_HEIGHT - 22, 0x6D4725);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Page(Component title, List<Component> lines) {}

    private enum Section {
        WELCOME("gui.hogwartsmagic.hogwarts_guide.section.welcome", List.of(
                page("A Letter for New Witches and Wizards", List.of(
                        "Welcome, young witch or wizard. If this handbook has found its way into your hands, your first steps into a larger magical life have already begun.",
                        "Do not be troubled if everything feels vast. HogwartsMagic is a world of study, strange creatures, hidden routes, enchanted tools, and spells best learned with patience rather than panic.",
                        "You need not master everything at once. Begin with a wand, a few humble spells, and enough good sense to know when to run."
                )),
                page("Your first days", List.of(
                        "Read your invitation and keep this guide close.",
                        "Find or craft a wand before chasing grander ambitions.",
                        "Study spellbooks whenever fortune places one in your path, for learned magic stays with you.",
                        "When the moment feels right, place the Sorting Hat upon your head and discover the house that will shape your path."
                ))
        )),
        SURVIVAL("gui.hogwartsmagic.hogwarts_guide.section.survival", List.of(
                page("Staying alive", List.of(
                        "Mana is precious in the beginning. A clever charm cast at the right moment is worth more than three dramatic mistakes.",
                        "Lumos, Alohomora, Accio, Arresto Momentum, and other practical magic will often serve you better than reckless curses.",
                        "Keep food on hand, carry a mundane weapon, and remember that courage does not require foolishness."
                )),
                page("Advice from older students", List.of(
                        "Face dangerous enemies in open places where movement and escape are still possible.",
                        "Use control spells before damage spells when panic sets in. A frozen foe is easier to survive than a merely injured one.",
                        "Do not wander into the darkest corners of the forest or the world until you have prepared for what hunts there."
                ))
        )),
        SPELLS("gui.hogwartsmagic.hogwarts_guide.section.spells", List.of(
                page("On the study of spells", List.of(
                        "Spellbooks are the proper way to grow your magical repertoire. Once a spell is learned, it may be selected on your wand and arranged for quick use.",
                        "There is no shame in relying on simple charms. Every accomplished duelist knows that timing and control matter more than spectacle."
                )),
                page("Useful magical disciplines", List.of(
                        "Practical charms: Lumos, Alohomora, Revelio, Reparo.",
                        "Control and defense: Glacius, Wingardium Leviosa, Stupefy, Petrificus Totalus, Protego.",
                        "Battle magic: Confringo, Bombarda, Sectumsempra, Diffindo.",
                        "Forbidden and dangerous arts: Crucio, Imperio, Fiendfyre, Avada Kedavra, Morsmordre."
                ))
        )),
        SYSTEMS("gui.hogwartsmagic.hogwarts_guide.section.systems", List.of(
                page("Ways of the wider magical world", List.of(
                        "Houses grant identity and subtle advantage.",
                        "Wand cores change the temperament of your casting.",
                        "Relics strengthen your reserves or replenish your magic more swiftly.",
                        "Owls and Floo hearths tie distant places together once you have built the right infrastructure."
                )),
                page("Advanced mysteries", List.of(
                        "Cave Inimicum wards may conceal what lies within from unfriendly eyes.",
                        "Polyjuice allows disguise, though not always wisdom.",
                        "Horcrux rites and the blackest branches of magic are best left for the truly prepared, or the truly lost."
                ))
        )),
        CREATURES("gui.hogwartsmagic.hogwarts_guide.section.creatures", List.of(
                page("Friends and terrors", List.of(
                        "Not every beast in this world is an enemy. Owls and Hippogriffs may become treasured companions, and a Patronus can be a shining guardian in darker hours.",
                        "Yet other beings are no mere nuisance. Death Eaters, Dementors, Basilisks, and forest horrors demand respect."
                )),
                page("Practical warnings", List.of(
                        "If a foe commands spells, never stand still for long.",
                        "If a creature rules the sky, answer with movement or cover.",
                        "If a place feels ancient, cursed, or unwelcome, trust that feeling."
                )),
                page("On house elves", List.of(
                        "A wild house elf keeps to itself deep in the Forbidden Forest, always alone. A few still keep the kitchens of the castle.",
                        "It vanishes with a crack the instant it notices a wizard, so you must either go unnoticed - an Invisibility Cloak, an invisibility potion, Polyjuice worn as some beast, or a slow and patient crouch - or hold it fast where it stands.",
                        "Cave Inimicum forbids Apparition within its bounds. Petrificus Totalus, Incarcerous, Glacius, Stupefy, Arresto Momentum and the Imperius Curse will each serve as well.",
                        "Offer food to an elf that cannot flee and it will bind itself to you as master."
                )),
                page("On the basilisk", List.of(
                        "The King of Serpents runs fourteen paces from snout to tail tip, and its stare is death. Not at once - it takes hold slowly, and the world goes grey at the edges while it works.",
                        "Look away and the hold breaks. Break the line of sight and it breaks faster. Fight it by the edges of your vision, never square on, and never stand admiring it.",
                        "It rears before it strikes, which is the only warning you are given. At a distance it spits venom that eats the ground where it lands, and if you keep out of reach it will simply go under the earth and come up beneath you.",
                        "Blows landed on the head will in time put its eyes out, and a blinded basilisk cannot petrify at all. It hunts by sound thereafter - so walk, do not run, and crouch when it casts about for you."
                )),
                page("On the merpeople", List.of(
                        "The Black Lake is inhabited. Deep down, past where the light gives out, there is a village of stone dwellings under a great statue of a merchieftainess, and the people who built it are watching you swim over their roofs.",
                        "They are Beings, not beasts, and they will let you pass. Linger over the village and one will rear up, spread its fins and cry out at you - a warning, and the only one you are given. Take it and go, and nothing happens at all.",
                        "Ignore it, or raise a hand to any one of them, and every merperson for twenty yards comes at once with a spear. They are faster than you in the water and they know it. Their chieftainess is larger, paler, and worse.",
                        "Their speech is a real language, though above the surface it is nothing but a hideous screeching. Put your head under and listen properly and you will hear them singing.",
                        "A spear taken from one is a poor weapon in air and a wicked one below the water.",
                        "There is a hoard laid at the statue's feet, and a second stash behind the dwellings. Lift the lid of either and every merperson in the water knows it, at once, and they do not forget it quickly. Whatever is in there, decide first whether you can swim faster than they can.",
                        "In the hoard lies a crown of a chieftainess long drowned. Wear it and the lake is yours: you will breathe its water and see through it as though you were born to it, and you will never need Gillyweed again.",
                        "But wear it where they can see you and there is no rearing up, no screeching, no warning of any kind. You are wearing their chieftainess. They come at once, and they keep coming. You may have the freedom of the lake or the goodwill of the people in it. You will not have both."
                )),
                page("On grindylows", List.of(
                        "A grindylow is a small horned water demon, sickly green, with a head too big for it, a mouth full of needles and six curling tentacles where its legs ought to be. The merpeople keep them the way a farmer keeps dogs.",
                        "It will not duel you. It lies in the weed doing nothing whatever until something wakes it, and then it comes with its fellows, and what it does when it reaches you is not to wound you. It takes hold of your leg, drags you down and waits.",
                        "There is a nest of them asleep around the hoard. Open that chest and you will find out exactly what they were put there for.",
                        "Do not thrash. Their fingers are long and strong and they are also brittle: strike the thing holding you, however feebly, and it lets go at once. Then deal with the next one.",
                        "You can see them before they see you, if you look. Their eyes carry a light of their own, and two green points low in the weed are the only warning you will get."
                )),
                page("On giants", List.of(
                        "A giant stands more than three men tall and wanders the deepest forest alone, muttering to itself and pulling at the trees.",
                        "Left be, it will leave you be. Strike it, or linger too long at its feet, and it will announce itself with a roar you will feel through the soles of your boots.",
                        "It swings a backhand that will throw you clear across a clearing, and kicks the ground apart - earth, timber, glass and thatch all give way, though good stone will not.",
                        "Worst of all, it will simply pick you up and throw you. Do not count on the landing. It is no gentler with the beasts of the forest than it is with wizards."
                )),
                page("On the Whomping Willow", List.of(
                        "There is a tree in the old woods that is not only a tree. It stands still while you are far off, and it is still standing still when you are close enough to regret it.",
                        "It cannot follow you. That is the whole of the trick: everything it can reach, it can reach at once, and everything past that is safe. Watch which way a branch draws back - sideways for a sweep, straight up for a hammer, and all four at once when it means to shake the ground.",
                        "Fly over it at your peril. It will pick a broom out of the air, though a rider so struck seems to fall more gently than they ought.",
                        "Look low on the trunk for the knot. Press it and the whole tree goes slack, and stays so until something disturbs it. Those of a practical bent have been known to run a wire to it and lay a plate in the path, so that the tree sleeps for anyone who walks the road and wakes for anyone who does not.",
                        "Fire is what it fears; an axe is next best. Arrows only lodge in the bark, and left alone it closes its own wounds.",
                        "Cut one down and you may find a whip of it still living. Plant that, let it come up, and take it by the hand when it is grown - it will know you, and it will know everyone else is not you."
                )),
                page("On freeing an elf", List.of(
                        "A bound elf serves, and blames itself with its own fists whenever harm comes to its master.",
                        "Present it with clothing - any garment at all - and it is free.",
                        "But it remembers how it was taken. One brought in gently stays on as a friend, Apparating to your side and hurling aside any who raise a hand to you.",
                        "One taken by force takes the clothes, and is gone. Choose accordingly."
                ))
        )),
        CRAFTING("gui.hogwartsmagic.hogwarts_guide.section.crafting", List.of(
                page("Of benches and craft", List.of(
                        "The Wandmaker's Bench is where proper wands are born from fine wood, magical cores, and careful intention.",
                        "The Broom Workbench requires ingredients first and Alatus afterward, for flight in this world is infused rather than simply assembled.",
                        "Rare creature parts, arcane materials, and relics are not trinkets. Keep them."
                )),
                page("What to build first", List.of(
                        "A trustworthy wand should come before vanity.",
                        "A broom is more than a luxury; it is often the difference between escape and disaster.",
                        "Treat every new station as a doorway into another branch of magical life."
                ))
        )),
        POTIONS("gui.hogwartsmagic.hogwarts_guide.section.potions", List.of(
                page("The Wizard Cauldron", List.of(
                        "Set the cauldron over heat: a lit campfire or soul campfire, fire, soul fire, lava or a magma block.",
                        "Fill it with a water bucket, or three water bottles. Ingredients go only into a full cauldron.",
                        "Right-click with each ingredient, one at a time. Stir with a Fine Wood Stick: right-click to stir clockwise, sneak and right-click to stir anticlockwise (keep your other hand empty).",
                        "Simmer steps count down only while there is heat. Right-click with an empty hand to read the brew."
                )),
                page("Signs in the liquid", List.of(
                        "Every correct step changes the colour of the brew. A wrong step turns it grey-brown with a hiss of smoke: the brew is ruined.",
                        "A finished brew shimmers and chimes. Left on the heat more than 120 seconds after its last simmer, it boils into ruin. Take the heat away and it keeps forever.",
                        "A glass bottle takes one dose, and a brew gives three. A ruined brew bottles as Failed Potion, or a bucket pours it away."
                )),
                page("Wiggenweld Potion", List.of(
                        "Heals 8 health over two seconds and stops Bleeding.",
                        "1. Add 1 Honey Bottle.",
                        "2. Add 2 Glow Berries.",
                        "3. Stir clockwise 3 times.",
                        "4. Simmer 20 seconds."
                )),
                page("Pepperup Potion", List.of(
                        "Restores 40 mana and gives 30 seconds of Fire Resistance. Steam pours from your ears.",
                        "1. Add 1 Blaze Powder.",
                        "2. Add 1 Mandrake Root.",
                        "3. Stir anticlockwise 2 times.",
                        "4. Simmer 15 seconds."
                )),
                page("Polyjuice Potion", List.of(
                        "Turns the drinker into whoever the sample came from.",
                        "1. Add 1 Acromantula Venom.",
                        "2. Add 2 Mandrake Roots.",
                        "3. Add 1 Gillyweed.",
                        "4. Stir clockwise 4 times.",
                        "5. Simmer 480 seconds (8 minutes)."
                )),
                page("Polyjuice: the sample", List.of(
                        "6. Add a filled Polyjuice Sample. An empty one is refused. Take the cauldron off the heat while you fetch the sample, or it overbrews.",
                        "Then, if you like, in this order: 1 Redstone for an 8-minute disguise, then 1 Gunpowder to make every dose a splash potion.",
                        "The brew gives 3 doses, each carrying the same sample. The brewing stand no longer makes Polyjuice."
                ))
        ));

        private final Component label;
        private final List<Page> pages;

        Section(String labelKey, List<Page> pages) {
            this.label = Component.translatable(labelKey);
            this.pages = pages;
        }

        public Component label() {
            return this.label;
        }

        public List<Page> pages() {
            return this.pages;
        }

        private static Page page(String title, List<String> lines) {
            List<Component> components = new ArrayList<>();
            for (String line : lines) {
                components.add(Component.literal(line));
            }
            return new Page(Component.literal(title), components);
        }
    }
}
