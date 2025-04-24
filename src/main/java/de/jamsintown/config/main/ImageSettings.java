package de.jamsintown.config.main;

public class ImageSettings {
    /**
     * gphoto2 --get-config='/main/imgsettings/imageformat'
     * Label: Bildformat
     * Readonly: 0
     * Type: RADIO
     * Current: Large Fine JPEG
     * Choice: 0 Large Fine JPEG
     * Choice: 1 Large Normal JPEG
     * Choice: 2 Medium Fine JPEG
     * Choice: 3 Medium Normal JPEG
     * Choice: 4 Small Fine JPEG
     * Choice: 5 Small Normal JPEG
     * Choice: 6 Kleineres JPEG
     * Choice: 7 RAW + Large Fine JPEG
     * Choice: 8 RAW + Large Normal JPEG
     * Choice: 9 RAW + Medium Fine JPEG
     * Choice: 10 RAW + Medium Normal JPEG
     * Choice: 11 RAW + Small Fine JPEG
     * Choice: 12 RAW + Small Normal JPEG
     * Choice: 13 RAW + Kleineres JPEG
     * Choice: 14 cRAW + Large Feines JPEG
     * Choice: 15 cRAW + Large Normal JPEG
     * Choice: 16 cRAW + Mittel Feines JPEG
     * Choice: 17 cRAW + Medium Normal JPEG
     * Choice: 18 cRAW + Kleines Feines JPEG
     * Choice: 19 cRAW + Small Normal JPEG
     * Choice: 20 cRAW + Kleineres JPEG
     * Choice: 21 RAW
     * Choice: 22 cRAW
     * END
     */
    public String mainImgsettingsImageformat;
    /**
     * Label: Bild Format SD
     * Readonly: 0
     * Type: RADIO
     * Current: Large Fine JPEG
     * Choice: 0 Large Fine JPEG
     * Choice: 1 Large Normal JPEG
     * Choice: 2 Medium Fine JPEG
     * Choice: 3 Medium Normal JPEG
     * Choice: 4 Small Fine JPEG
     * Choice: 5 Small Normal JPEG
     * Choice: 6 Kleineres JPEG
     * Choice: 7 RAW + Large Fine JPEG
     * Choice: 8 RAW + Large Normal JPEG
     * Choice: 9 RAW + Medium Fine JPEG
     * Choice: 10 RAW + Medium Normal JPEG
     * Choice: 11 RAW + Small Fine JPEG
     * Choice: 12 RAW + Small Normal JPEG
     * Choice: 13 RAW + Kleineres JPEG
     * Choice: 14 cRAW + Large Feines JPEG
     * Choice: 15 cRAW + Large Normal JPEG
     * Choice: 16 cRAW + Mittel Feines JPEG
     * Choice: 17 cRAW + Medium Normal JPEG
     * Choice: 18 cRAW + Kleines Feines JPEG
     * Choice: 19 cRAW + Small Normal JPEG
     * Choice: 20 cRAW + Kleineres JPEG
     * Choice: 21 RAW
     * Choice: 22 cRAW
     * END
     */
    public String mainImgsettingsImageformatsd;
    /**
     * Label: Bild Format CF
     * Readonly: 0
     * Type: RADIO
     * Current: Large Fine JPEG
     * Choice: 0 Large Fine JPEG
     * Choice: 1 Large Normal JPEG
     * Choice: 2 Medium Fine JPEG
     * Choice: 3 Medium Normal JPEG
     * Choice: 4 Small Fine JPEG
     * Choice: 5 Small Normal JPEG
     * Choice: 6 Kleineres JPEG
     * Choice: 7 RAW + Large Fine JPEG
     * Choice: 8 RAW + Large Normal JPEG
     * Choice: 9 RAW + Medium Fine JPEG
     * Choice: 10 RAW + Medium Normal JPEG
     * Choice: 11 RAW + Small Fine JPEG
     * Choice: 12 RAW + Small Normal JPEG
     * Choice: 13 RAW + Kleineres JPEG
     * Choice: 14 cRAW + Large Feines JPEG
     * Choice: 15 cRAW + Large Normal JPEG
     * Choice: 16 cRAW + Mittel Feines JPEG
     * Choice: 17 cRAW + Medium Normal JPEG
     * Choice: 18 cRAW + Kleines Feines JPEG
     * Choice: 19 cRAW + Small Normal JPEG
     * Choice: 20 cRAW + Kleineres JPEG
     * Choice: 21 RAW
     * Choice: 22 cRAW
     * END
     */
    public String mainImgsettingsImageformatcf;
    /**
     * Label: ISO-Geschwindigkeit
     * Readonly: 0
     * Type: RADIO
     * Current: 125
     * Choice: 0 Auto
     * Choice: 1 100
     * Choice: 2 125
     * Choice: 3 160
     * Choice: 4 200
     * Choice: 5 250
     * Choice: 6 320
     * Choice: 7 400
     * Choice: 8 500
     * Choice: 9 640
     * Choice: 10 800
     * Choice: 11 1000
     * Choice: 12 1250
     * Choice: 13 1600
     * Choice: 14 2000
     * Choice: 15 2500
     * Choice: 16 3200
     * Choice: 17 4000
     * Choice: 18 5000
     * Choice: 19 6400
     * Choice: 20 8000
     * Choice: 21 10000
     * Choice: 22 12800
     * Choice: 23 16000
     * Choice: 24 20000
     * Choice: 25 25600
     */
    public Integer mainImgsettingsIso;
    /**
     * Label: Weißabgleich
     * Readonly: 0
     * Type: RADIO
     * Current: Auto
     * Choice: 0 Auto
     * Choice: 1 AWB White
     * Choice: 2 Tageslicht
     * Choice: 3 Schatten
     * Choice: 4 Wolkig
     * Choice: 5 Kunstlicht - Glühlampe
     * Choice: 6 Kunstlicht (Leuchtstofflampe)
     * Choice: 7 Blitz
     * Choice: 8 Manuell
     * Choice: 9 Farbtemperatur
     * END
     */

    public String mainImgsettingsWhitebalance;
    /**
     * Label: Farbtemperatur
     * Readonly: 0
     * Type: RADIO
     * Current: 5200
     * Choice: 0 2500
     * Choice: 1 2600
     * Choice: 2 2700
     * Choice: 3 2800
     * Choice: 4 2900
     * Choice: 5 3000
     * Choice: 6 3100
     * Choice: 7 3200
     * Choice: 8 3300
     * Choice: 9 3400
     * Choice: 10 3500
     * Choice: 11 3600
     * Choice: 12 3700
     * Choice: 13 3800
     * Choice: 14 3900
     * Choice: 15 4000
     * Choice: 16 4100
     * Choice: 17 4200
     * Choice: 18 4300
     * Choice: 19 4400
     * Choice: 20 4500
     * Choice: 21 4600
     * Choice: 22 4700
     * Choice: 23 4800
     * Choice: 24 4900
     * Choice: 25 5000
     * Choice: 26 5100
     * Choice: 27 5200
     * Choice: 28 5300
     * Choice: 29 5400
     * Choice: 30 5500
     * Choice: 31 5600
     * Choice: 32 5700
     * Choice: 33 5800
     * Choice: 34 5900
     * Choice: 35 6000
     * Choice: 36 6100
     * Choice: 37 6200
     * Choice: 38 6300
     * Choice: 39 6400
     * Choice: 40 6500
     * Choice: 41 6600
     * Choice: 42 6700
     * Choice: 43 6800
     * Choice: 44 6900
     * Choice: 45 7000
     * Choice: 46 7100
     * Choice: 47 7200
     * Choice: 48 7300
     * Choice: 49 7400
     * Choice: 50 7500
     * Choice: 51 7600
     * Choice: 52 7700
     * Choice: 53 7800
     * Choice: 54 7900
     * Choice: 55 8000
     * Choice: 56 8100
     * Choice: 57 8200
     * Choice: 58 8300
     * Choice: 59 8400
     * Choice: 60 8500
     * Choice: 61 8600
     * Choice: 62 8700
     * Choice: 63 8800
     * Choice: 64 8900
     * Choice: 65 9000
     * Choice: 66 9100
     * Choice: 67 9200
     * Choice: 68 9300
     * Choice: 69 9400
     * Choice: 70 9500
     * Choice: 71 9600
     * Choice: 72 9700
     * Choice: 73 9800
     * Choice: 74 9900
     * Choice: 75 10000
     * END
     */
    public String mainImgsettingsColortemperature;
    /**
     * Label: Weißabgleich Anpassung A
     * Readonly: 0
     * Type: RADIO
     * Current: 0
     * Choice: 0 -9
     * Choice: 1 -8
     * Choice: 2 -7
     * Choice: 3 -6
     * Choice: 4 -5
     * Choice: 5 -4
     * Choice: 6 -3
     * Choice: 7 -2
     * Choice: 8 -1
     * Choice: 9 0
     * Choice: 10 1
     * Choice: 11 2
     * Choice: 12 3
     * Choice: 13 4
     * Choice: 14 5
     * Choice: 15 6
     * Choice: 16 7
     * Choice: 17 8
     * Choice: 18 9
     * END
     */
    public String mainImgsettingsWhitebalanceadjusta;
    /**
     * Label: Weißabgleich Anpassung B
     * Readonly: 0
     * Type: RADIO
     * Current: 0
     * Choice: 0 -9
     * Choice: 1 -8
     * Choice: 2 -7
     * Choice: 3 -6
     * Choice: 4 -5
     * Choice: 5 -4
     * Choice: 6 -3
     * Choice: 7 -2
     * Choice: 8 -1
     * Choice: 9 0
     * Choice: 10 1
     * Choice: 11 2
     * Choice: 12 3
     * Choice: 13 4
     * Choice: 14 5
     * Choice: 15 6
     * Choice: 16 7
     * Choice: 17 8
     * Choice: 18 9
     * END
     */
    public String mainImgsettingsWhitebalanceadjustb;
    /**
     * Label: Weißabgleich X A
     * Readonly: 1
     * Type: RADIO
     * Current: 0
     * Choice: 0 0
     * Choice: 1 1
     * Choice: 2 2
     * Choice: 3 3
     * END
     */
    public String mainImgsettingsWhitebalancexa;
    /**
     *Label: Weißabgleich X B
     * Readonly: 1
     * Type: RADIO
     * Current: 0
     * Choice: 0 0
     * Choice: 1 1
     * Choice: 2 2
     * Choice: 3 3
     * END
     */
    public String mainImgsettingsWhitebalancexb;
    /**
     * Label: Farbraum
     * Readonly: 0
     * Type: RADIO
     * Current: sRGB
     * Choice: 0 sRGB
     * Choice: 1 AdobeRGB
     * END
     */
    public String mainImgsettingsColorspace;
}
