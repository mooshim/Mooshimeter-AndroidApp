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
        StringBuilder outbuilder = new StringBuilder();
        for(char c : in.toCharArray()) {
            switch(c) {
                case '-':
                    outbuilder.append("neg ");
                    break;
                case 'm':
                    outbuilder.append(" emm");
                    break;
                case 'k':
                    outbuilder.append(" kay");
                    break;
                case 'M':
                    outbuilder.append(" mega");
                    break;
                default:
                    outbuilder.append(c);
                    break;
            }
        }
        return outbuilder.toString();
    }
    public boolean decideAndSpeak(final float new_value, final String value_label) {
        if( timer.expired
            || (!Util.isSpeaking() && abs(last_value - new_value)>abs(0.20*new_value))) {
            // If the value has changed 20%, or just every 5 second
            last_value = new_value;
            Util.speak(formatValueLabelForSpeaking(value_label));
            timer.fire(5000);
            return true;
        }
        return false;
    }
}
