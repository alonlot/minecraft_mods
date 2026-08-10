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
                page("On giants", List.of(
                        "A giant stands more than three men tall and wanders the deepest forest alone, muttering to itself and pulling at the trees.",
                        "Left be, it will leave you be. Strike it, or linger too long at its feet, and it will announce itself with a roar you will feel through the soles of your boots.",
                        "It swings a backhand that will throw you clear across a clearing, and kicks the ground apart - earth, timber, glass and thatch all give way, though good stone will not.",
                        "Worst of all, it will simply pick you up and throw you. Do not count on the landing. It is no gentler with the beasts of the forest than it is with wizards."
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
