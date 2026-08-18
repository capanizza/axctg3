package br.com.axialsoftware.axctg3.service.fiscal;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Cálculo da chave de acesso da NFe (44 dígitos: cUF+AAMM+CNPJ+mod+serie+nNF+tpEmis+cNF+cDV,
 * conforme Manual de Orientação do Contribuinte, leiaute 4.00) — parte da emissão própria
 * (docs/EMISSAO-NFE.md). {@code cNF} é gerado aleatório (não pode ser previsível/sequencial,
 * por especificação); {@code cDV} é o dígito verificador módulo 11 sobre os 43 dígitos
 * anteriores.
 */
@Service
public class NfeChaveService {

    private final SecureRandom random = new SecureRandom();

    /**
     * @param cUf         código IBGE da UF do emitente (2 dígitos, ex.: 35 para SP)
     * @param dataEmissao data de emissão da NFe (usa ano/mês, formato AAMM)
     * @param cnpjEmitente CNPJ do emitente, com ou sem máscara (só os dígitos são usados)
     * @param modelo      modelo do documento fiscal (55 para NFe)
     * @param serie       série da nota
     * @param numero      número da nota
     * @param tpEmis      forma de emissão (1 = normal)
     * @param cNf         código numérico aleatório de 8 dígitos (gerar com {@link #gerarCNf()})
     * @return chave de acesso completa, 44 dígitos
     */
    public String gerarChave(Integer cUf, LocalDate dataEmissao, String cnpjEmitente, Integer modelo,
                              Integer serie, Integer numero, Integer tpEmis, Integer cNf) {
        String aamm = String.format("%02d%02d", dataEmissao.getYear() % 100, dataEmissao.getMonthValue());
        String cnpjSoDigitos = somenteDigitos(cnpjEmitente);
        String chave43 = String.format("%02d", cUf)
                + aamm
                + String.format("%014d", Long.parseLong(cnpjSoDigitos))
                + String.format("%02d", modelo)
                + String.format("%03d", serie)
                + String.format("%09d", numero)
                + String.format("%01d", tpEmis)
                + String.format("%08d", cNf);
        return chave43 + calcularDv(chave43);
    }

    /** Código numérico aleatório de 8 dígitos (campo cNF) — não sequencial, por especificação. */
    public Integer gerarCNf() {
        return random.nextInt(100_000_000);
    }

    /**
     * Dígito verificador módulo 11: pesos 2..9 cíclicos aplicados da direita pra esquerda,
     * soma dos produtos, resto = soma % 11; resto 0 ou 1 → DV 0, senão DV = 11 - resto.
     */
    public int calcularDv(String chave43) {
        int soma = 0;
        int peso = 2;
        for (int i = chave43.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(chave43.charAt(i));
            soma += digito * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? 0 : 11 - resto;
    }

    private String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
