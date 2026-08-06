package br.com.axialsoftware.axctg3.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.text.MaskFormatter;
import java.text.ParseException;

@Service
public class FormatacaoService {

    private final UtilGeralService utilGeralService;

    public FormatacaoService(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    public String formatacaoMascContabil(String value) {
        if (value == null) {
            return null;
        }
        String ret = null;
        String masc = utilGeralService.getMascContabil();
        try {
            MaskFormatter mascContabil = new MaskFormatter(masc);
            mascContabil.setValueContainsLiteralCharacters(false);
            ret = mascContabil.valueToString(value);
        } catch (ParseException e) {
            Logger log = LoggerFactory.getLogger(FormatacaoService.class);
            log.error(e.getMessage());
        }
        return ret;
    }

    public String formatacaoCnpj(String value) {
        if (value == null) {
            return null;
        }
        String ret = value.trim();
        String masc;
        if (ret.length() == 11) {
            masc = "###.###.###-##";
        } else {
            if (ret.length() == 14) {
                masc = "##.###.###/####-##";
            } else {
                return ret;
            }
        }
        try {
            MaskFormatter mascCnpj = new MaskFormatter(masc);
            mascCnpj.setValueContainsLiteralCharacters(false);
            return mascCnpj.valueToString(ret);
        } catch (ParseException e) {
            return null;
        }
    }
}