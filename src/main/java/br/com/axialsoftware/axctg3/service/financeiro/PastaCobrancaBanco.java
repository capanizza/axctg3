package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Resolve {@code <pastaRemessa>/<codGeral 3 dígitos>/<aaaamm>} — pasta cadastrada em
 * {@link Banco#getPastaRemessa()} ("pasta para gravação" no cadastro do banco), com uma
 * subpasta por banco (código Febraban) e uma por mês corrente. Raiz compartilhada entre
 * {@link RemessaBancoService} (grava o arquivo de remessa direto nela) e
 * {@link BoletoService} (grava os PDFs de boleto numa subpasta {@code pdf} dentro dela).
 */
final class PastaCobrancaBanco {

    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");

    private PastaCobrancaBanco() {
    }

    static Path resolver(Banco banco, LocalDate data) {
        if (banco.getPastaRemessa() == null || banco.getPastaRemessa().isBlank()) {
            throw new IllegalArgumentException(
                    "Banco " + banco.getNome() + " não tem pasta de gravação cadastrada (aba de cobrança bancária)");
        }
        return Path.of(banco.getPastaRemessa())
                .resolve(String.format("%03d", banco.getCodGeral()))
                .resolve(data.format(AAAAMM));
    }
}
