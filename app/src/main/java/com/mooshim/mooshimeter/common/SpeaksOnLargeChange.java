package com.mooshim.mooshimeter.common;
import static java.lang.Math.abs;

/**
 * Created by First on 3/7/2016.
 */
public class SpeaksOnLargeChange {
    private float last_value = 0;
    private CooldownTimer timer = new CooldownTimer();
    private String formatValueLabelForSpeaking(String in) {
        // Changes suffixes to speech-friendly versions (eg. "m" is rendered as "meters", which is wrong)
        if(in==null||in.equals("")){
            return "";
        }

        // If there are no numbers in the input (such as "OUT OF RANGE"), don't treat it as numeric.
        if (!in.matches(".*[0-9].*")) {
            return in;
        }

        StringBuilder outbuilder = new StringBuilder();
        for(char c : in.toCharArray()) {
            switch(c) {
                case '-':
                    outbuilder.append("neg ");
                    break;
                case 'm':
                    outbuilder.append(" milli ");
                    break;
                case 'k':
                    outbuilder.append(" kilo ");
                    break;
                case 'M':
                    outbuilder.append(" mega ");
                    break;
                case 'A':
                    outbuilder.append(" amps ");
                    break;
                case 'V':
                    outbuilder.append(" volts ");
                    break;
                case '\u03A9':
                    outbuilder.append(" ohms ");
                    break;
                case 'W':
                    outbuilder.append(" watts ");
                    break;
                case 'F':
                    outbuilder.append(" fahrenheit ");
                    break;
                case 'C':
                    outbuilder.append(" celsius ");
                    break;
                default:
                    outbuilder.append(c);
                    break;
            }
        }
        return outbuilder.toString();
    }
    public boolean decideAndSpeak(MeterReading val) {
        double threshold = Math.max(abs(0.20 * val.value), abs(0.05 * val.getMax()));
        double change = abs(last_value - val.value);
        if (Util.isSpeaking()) return false;
        if( timer.expired
            || (change>threshold)) {
            // If the value has changed 20%, or just every 5 second
            last_value = val.value;
            Util.speak(formatValueLabelForSpeaking(val.toString()));
            timer.fire(10000);
            return true;
        }
        return false;
    }
}
