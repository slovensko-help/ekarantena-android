# ![](app/src/quarantine/res/mipmap-xxhdpi/ic_launcher.png) eKaranténa - Android

![Android 5.0](https://img.shields.io/static/v1?message=5.0&color=green&logo=android&label=Android) ![Google Play Services 13.0](https://img.shields.io/static/v1?message=13.0&color=green&logo=google-play&label=Google%20Play%20Services)

*Pre anglickú verziu tohoto README, chodte na [README.md](README.md).*

Táto aplikácia slúži ako nástroj na monitorovanie dodržiavania domácej izolácie nariadenej úradmi verejného zdravotníctva.
Umožňuje úradu vykonávať risk assesment používateľov v domácej izolácii, detekovať ich porušenia
domácej izolácie a vykonávať lepšie zamerané osobné kontroly domácej izolácie, pričom zachováva
súkromie používateľa v najvyššej možnej miere. Aplikácia nemá za cieľ znemožniť používateľom
odísť z miesta ich domácej izolácie, keďže toto je problém neriešiteľný pomocou aplikácie.

Táto aplikácia je momentálne nasadená ako alternatíva štátnej karantény v Slovenskej republike a to
[Úradom verejného zdravotnícva](http://www.uvzsr.sk/), jej prevádzkou je poverené
[Národné centrum zdravotníckych informácii](http://www.nczisk.sk/Pages/default.aspx). Táto verzia aplikácie
sa dá stiahnuť z [Google Play store](https://play.google.com/store/apps/details?id=sk.nczi.ekarantena) a
[Apple app store](https://apps.apple.com/sk/app/ekarantena-slovensko/id1513127897). Viac informácii o aplikácii
sa dá nájsť na webe [korona.gov.sk/ekarantena/](https://korona.gov.sk/ekarantena). Aplikácia bola vyvinutá
tímom dobrovoľníkov v spolupráci s Národným centrom zdravotníckych informácii (NCZI).

## Súkromie

Aplikácia bola navrhnutá aby rešpektovala súkromie používateľa. Využíva minimálne množstvo informácii na to
aby fungovala. Tieto informácie tiež spracúva iba lokálne, na server/úradu sú zasielané iba informácie o
porušení domácej izolácie.

Aplikácia používa citlivé údaje osoby, tvárovú biometriku na overenie prítomnosti správnej osoby pri zariadení
a tiež polohu zariadenia na overenie prítomnosti zariadenia v mieste domácej izolácie. Tieto dáta sa spracúvajú
len lokálne na zariadení, sú vyhodnotené voči uloženej biometrickej predlohe osoby a voči uloženej polohe miesta
domácej izolácie. Aplikácia neukladá polohu zariadenia, využíva ju len na overenie voči uloženej polohe miesta
domácej izolácie.

## Funkcionalita

Aplikácia obsahuje niekoľko spôsobov ktorými monitoruje dodržiavanie domácej izolácie, pasívny (notifikácia pri opustení miesta)
a aktívny (overenie prítomnosti).

### Notifikácia pri opustení miesta

Aplikácia periodicky kontroluje polohu zariadenia a porovnáva ju s koordinátmi miesta domácej izolácie.
Pokiaľ sa zariadenie nachádza ďalej od miesta domácej izolácie ako nastavená vzdialenosť (pričom
sa počíta s momentálnou presnosťou určenia polohy zariadením), na server je zaslaná notifikácia o
porušení domácej izolácie spolu s mierou porušenia (vzdialenosť zariadenia od miesta domácej izolácie)
ale nie polohou zariadenia. Ak nieje dostupné internetové pripojenie, tieto notifikácie sú uložené
na zariadení a odoslané keď sa stane dostupným.

Spolu s periodickými kontrolami aplikácia monitoruje aj stav služieb ktoré sú vyžadované na chod aplikácie
na zariadení (povolené lokalizačné služby, povolený prístup ku kamere pre overenie prítomnosti, povolený
prístup k polohe zariadenia aj na pozadí, vypnuté simulovanie polohy a žiadne zakázané aplikácie) a zasiela
serveru heartbeat správy obsahujúce tieto informácie.

### Overenie prítomnosti

Server aplikácie vyzýva používateľov aby vykonali overenie prítomnosti v domácej izolácii niekoľkrát
za deň, v náhodných časových intervaloch. Osoba je na overenie prítomnosti vyzvaná pomocou SMS správy
od úradu. Overenie prítomnosti pozostáva z dvoch overení, overení polohy zariadenia v mieste
domácej izolácie an overení prítomnosti používateľa pri zariadení. Poloha zariadenia je overená
s použitím lokalizačných služieb systému (Geofencing API) ktoré interne využívajú GPS/Galileo/GLONASS (GNSS)
spolu s dátami o mobilnom pripojení zariadenia (Cell ID) a WiFi pripojení zariadenia. Prítomnosť správnej
osoby pri zariadení je overená pomocou tvárovej biometriky s aktívnym liveness checkom (sledovanie
pohybu očí pri náhodnom pohybe na obrazovke) a je porovnaná s uloženou predlohou.

## Bezpečnosť

*Plná dokumentácia k bezpečnostnému dizajnu aplikácie bude čoskoro dostupná, nasleduje krátke zhrnutie.*

Bezpečnosť tak citlivej aplikácie akou je eKaranténa je veľmi dôležitá. Bezpečnostný dizajn
aplikácie mal dva ciele. **Prvým cieľom bolo zabezpečiť a ochrániť používateľské dáta.** **Druhým
cieľom bolo zabezpečiť aplikáciu pred pokusmi o podvádzanie v domácej izolácii zo strany používateľov.**

Pre splnenie prvého cieľu minimalizujeme množstvo informácii ktoré opúšťajú zariadenie používateľa.
Len porušenia domácej izolácie, spolu s heartbeat správami a odpoveďami na overenie prítomnosti sú
zasielané na server. Tieto správy však neobsahujú citlivé používateľské dáta a sú identifikované
len pseudonymnými identifikátormi používateľa a zariadenia. Takisto používame TLS s certificate pinningom
aby sme zaistili, že aplikácia bude komunikovať len so správnym backend serverom.

Na splnenie druhého cieľu využívame veľké množstvo opatrení proti niekoľkým útokom vrámci nášho modelu
útočníka (počítame s pomerne silným útočníkom ktorý má značné technické zručnosti). Vrámci nášho modelu
útočníka je niekoľko útokov:

 - Vzdialenie osoby z miesta izolácie s jej zariadením
 - GPS spoofing pomocou aplikácie an simulovanie GPS polohy
 - Kamera spoofing pomocou aplikácie na simulovanie kamery
 - Manipulácia s aplikáciou
 - Klon aplikácie
 - Enrollment inej osoby
 - Podvrhnutie biometriky
 - Zmena enrollment dát
 - Replay útoky

Nasledujúce útoky sú **mimo** nášho modelu útočníka:

 - Vzdialenie osoby z miesta izolácie bez jej zariadenia
 - Hardware GPS spoofing (vysielanie GPS signálu)

Opatrenia proti útokom vyššie sú nasledovné:

 - Detekovanie že zariadenie opustilo miesto domácej izolácie a nahlásenie porušenia.
 - Detekovanie simulovania polohy a inštalovaných aplikácii ktoré dokážu simulovať polohu.
 - Detekovanie nainštalovaných aplikácii ktoré umožňujú simulovať kameru.
 - Vykonávanie SafetyNet atestácii (a kontrola ich výsledkov na strane serveru) pri dôležitých
   úlohách v aplikácii, na detekciu rootnutia zariadenia a na overenie legitimity aplikácie.
 - Zaslanie PUSH notifikácie aplikácii, pomocou Firebase Cloud Messaging, ktorá obsahuje unikátny
   secret ktorý je vyžadovaný pti registrácii aplikácie na backend serveri, na overenie legitimity aplikácie.
 - Zaslanie HOTP secretu aplikácii, až po úspešnom overení jej legitimity, ktorý je využitý
   v HOTP challenge-response verifikácii na hraniciach/pri začiatku domácej izolácie medzi
   dôveryhodnou osobou a aplikáciou. Tento krok zaistí, že legitimita aplikácie je overená
   pri vpustení do domácej izolácie.
 - Overenie tvárovou biometrikou pod dozorom dôveryhodnej osoby na hraniciach/pri začiatku domácej
   izolácie, na verifikáciu, že v aplikácii je enrollovaná biometrika správnej osoby.
 - Liveness check počas overenia tvárovou biometrikou.
 - Využívanie ECDSA P-256 kľúča, uloženého v HW keystore, na podpisovanie všetkých aplikačných odpovedí serveru.
 - TLS certificate pinning.
 - Používanie náhodných hodnôt vygenerovaných serverom pri zasielaní odpovede aplikácie na presence check,
   na zastavenie replay útokov.
