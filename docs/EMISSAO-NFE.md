# Emissão própria de NFe (SOAP direto na SEFAZ-SP)

Registrado em 2026-08-17, a partir de uma conversa sobre como o axctg3 deveria se
relacionar com NFe. Reverte a decisão de 2026-08-09 documentada em `Nfe.java`
("não há integração com webservice da SEFAZ") — decisão consciente, não esquecimento.

## As três situações de NFe x notas fiscais

1. **NFe de entrada de terceiros** (compra) — fica pra depois, fora de escopo por ora.
2. **Importação de NFe produzida por outro sistema de vendas**, só pra cálculo fiscal —
   já coberto por `NfeImportService`/`NfeXmlParser`, sem mudança.
3. **Emissão de NFe a partir de `NotaSaida` lançada no sistema** — é o que este documento
   cobre. Substitui o caminho que existia no projeto legado
   `c:/projetos/netbeans/axial` (`NotaSaidaGridController.gravarXml`/`gravarXmlSimples`),
   que gerava um `.ini` e delegava tudo a um programa residente (ACBrMonitorPLUS) via
   protocolo de arquivo-texto. O Axial tinha duas versões quase-copiadas (regime normal
   com um `cst = 51` cravado no código pra produtor rural com diferimento, e Simples
   Nacional com CSOSN) e o bloco IBS/CBS incompleto (`cClassTrib` fixo, `gIBSCBS`
   comentado).

## Decisões

- **Transmissão via SOAP direto na SEFAZ** — sem gateway terceirizado (Focus NFe, NFe.io
  etc.). Sem custo por nota, sem dependência externa de produção; mais trabalho de
  manutenção (schema, assinatura, endpoint por UF/ambiente) fica por nossa conta.
- **Certificado só A1** (.pfx/.p12 + senha) — cobre o caso real hoje (Imbramil, Lavell).
  A3 (cartão/token) fica pra quando/se precisar.
- **Escopo desta rodada: só emissão** (criar → assinar → enviar → autorizar).
  Cancelamento, carta de correção e inutilização ficam pra depois.
- **Regime tributário modelado de verdade** em `Empresa.crt` (`CodRegimeTributario`,
  mesmo código CRT do leiaute) — motor único de geração de XML (`NfeXmlBuilder`),
  ramificado só onde o regime realmente muda a NFe (bloco ICMS/CSOSN via
  `ItemNotaSaida.cst`, sem hardcode). Resolve o problema dos dois arquivos quase-iguais
  do Axial.
- **UF de trabalho: SP** (Imbramil e Lavell). Endpoints de outras UFs entram depois, no
  mesmo mapa estático em `NfeWebserviceClient`.
- **Crédito de ICMS do Simples** (`pCredSN`/`vCredICMSSN`) — no Axial vinha de uma
  procedure do Firebird (`calculacsosn`) que não existe no axctg3. Fica **zerado** por
  enquanto: destrava a emissão; portar a regra é trabalho futuro.

## O que foi implementado

| Peça | Onde |
|---|---|
| Regime tributário (CRT) e ambiente (produção/homologação) | `entity/enums/CodRegimeTributario.java`, `entity/enums/AmbienteNfe.java`, campos novos em `Empresa` (aba "Emissão NFe") |
| Certificado A1 | `Empresa.certificadoArquivo` (`FileRef`/`jmix-localfs` — primeiro uso no projeto) + `Empresa.certificadoSenha` (sem criptografia, mesmo risco já aceito em `application.properties`) |
| Chave de acesso (44 dígitos, DV módulo 11) | `service/fiscal/NfeChaveService.java` — testado (`NfeChaveServiceTest`) contra cálculo manual |
| Código IBGE por UF | `service/fiscal/UfIbge.java` — tabela fixa, sem entidade de banco |
| Montagem do XML NFe 4.00 | `service/fiscal/NfeXmlBuilder.java` — mesmos grupos/tags que `NfeXmlParser` sabe ler de volta |
| Assinatura digital (XMLDSig, enveloped, C14N, RSA-SHA1) | `service/fiscal/NfeXmlSigner.java` — só `javax.xml.crypto.dsig` do JDK, sem dependência nova |
| Transmissão SOAP 1.2 + mTLS | `service/fiscal/NfeWebserviceClient.java` — endpoints SP conferidos em <https://portal.fazenda.sp.gov.br/servicos/nfe/Paginas/URL-WEBSERVICES.aspx> em 2026-08-17 |
| Orquestração + salvamento | `service/fiscal/NfeEmissaoService.java` — reaproveita `NfeImportService.salvarEmitida(byte[])` (novo método, mesmo bloco de `SaveContext` do import) pra gravar o resultado em `Nfe`/`NfeItem` |
| Teste isolado de certificado/mTLS | `NfeWebserviceClient.consultarStatusServico(Empresa)` — chama `NFeStatusServico4` (sem NFe nenhuma montada/assinada) — sugestão do usuário, mais simples que emitir de verdade e já confirma se cert+mTLS+conectividade funcionam. Botão "Testar conexão SEFAZ" na aba "Emissão NFe" do cadastro de Empresa (`EmpresaDetailView.java`) |
| UI (emissão) | dropDownButton "Ações" em `NotaSaidaListView`/`NfeListView` — item "Emitir NFe" |
| Cancelamento (evento `tpEvento` 110111) | `service/fiscal/NfeCancelamentoService.java` — monta/assina/transmite o evento via `NfeXmlSigner.assinarEvento`/`NfeWebserviceClient.enviarEvento` (endpoint `NFeRecepcaoEvento4`); grava protocolo/motivo do evento em campos novos de `Nfe` (`CANC_*`) preservando o protocolo de autorização original (`protNProt`), e atualiza `protCStat` pra 101. UI: item "Cancelar NFe" do mesmo dropDownButton, pede justificativa (mínimo 15 caracteres). **Validado em 2026-09-01** contra homologação SP: `cStat=135 "Evento registrado e vinculado a NF-e"` |

## Simplificações desta primeira versão (além das já citadas)

- **Sem diferimento/devolução de tributo no bloco IBS/CBS** — `gDif`/`gDevTrib`/`gRed`
  ficam zerados; só a alíquota "cheia" de `NaturezaOperacao` é aplicada.
- **Frete/transportador não modelados em `NotaSaida`** — `modFrete` fixo em 9 (sem
  transporte); sem grupo `transporta`/`veicTransp`.
- **`finNFe` sempre 1** (normal) — devolução (que mudaria pra 4) não é tratada aqui,
  mesma pendência já registrada em `docs/REFORMA-TRIBUTARIA-IBS-CBS.md`.
- **IPI simplificado**: `CST 50` fixo quando há valor, `IPINT`/sem grupo quando zero —
  `ItemNotaSaida` não guarda um CST de IPI próprio.
- **PIS/COFINS**: mesma lógica do Axial (alíquota zero, CST 01 se `NaturezaOperacao.venda`
  senão CST 06) — não há alíquota de PIS/COFINS por item ainda no cadastro.
- **`indFinal`/`indPres` fixos** (0 e 9) — não variam por `Parceiro`.

## Riscos conhecidos pra validar no teste real

- ~~Cadeia ICP-Brasil no truststore da JVM~~ — **aconteceu no primeiro teste** (`PKIX
  path building failed... unable to find valid certification path to requested
  target`), exatamente como previsto. Corrigido em 2026-08-17: em vez de mexer no
  `cacerts` do JDK do sistema (precisa de admin, não é reproduzível em outra máquina),
  a cadeia ICP-Brasil (intermediária "AC SOLUTI SSL EV G4" + raiz "Autoridade
  Certificadora Raiz Brasileira v10") foi embarcada como recurso do projeto
  (`nfe/icp-brasil-chain.pem`, confirmada via `openssl s_client` contra os dois
  endpoints SP + `openssl verify` contra a raiz baixada de acraiz.icpbrasil.gov.br) e
  mesclada com o truststore padrão da JVM em tempo de execução
  (`NfeWebserviceClient.trustManagerMesclado()` + `CompositeX509TrustManager`, novo).
  **Ainda não retestado** depois da correção.
- ~~HTTP/2~~ — **aconteceu no segundo teste**: `Received RST_STREAM: Use HTTP/1.1 for
  request`. O `HttpClient` do Java tenta negociar HTTP/2 por padrão; os webservices da
  SEFAZ (ASP.NET/.asmx) só falam HTTP/1.1 e derrubam a conexão. Corrigido forçando
  `.version(HttpClient.Version.HTTP_1_1)` no builder (`NfeWebserviceClient.enviar`).
  **Confirmado funcionando em 2026-08-17**: `consultarStatusServico` retornou
  `cStat=107 "Serviço em Operação"` contra homologação SP — certificado, mTLS e
  conectividade validados de ponta a ponta. Falta validar o fluxo completo de emissão
  (`NfeXmlBuilder`/assinatura/`NFeAutorizacao4`), que ainda não foi exercitado.
- **Endpoints SP** conferidos ao vivo em 2026-08-17 (emissão) e 2026-09-01
  (`NFeRecepcaoEvento4`, cancelamento), mas SEFAZ muda URL ocasionalmente — reconferir
  se a conexão falhar com erro de rede antes de suspeitar de outra coisa.
- **Canonicalização da assinatura** — um espaço a mais invalida a assinatura; testar
  contra homologação antes de qualquer nota de produção. **Confirmado funcionando**
  tanto pra `infNFe` (emissão, 2026-08-18) quanto pra `infEvento` (cancelamento,
  2026-09-01 — `NfeXmlSigner.assinarEvento`, mesma técnica de assinatura, tag
  diferente).
- **`tpNF` sempre "1" (saída), independente do CFOP** (`NfeXmlBuilder.construirIde`) —
  bug real encontrado em 2026-09-01 testando uma nota de "Compra de energia elétrica"
  (CFOP 1252, que começa em "1" = entrada): a SEFAZ rejeitou com `cStat=770 "CFOP
  Inexistente [... Tabela de CFOP(NT) está malformada ...]"`, mensagem confusa que na
  prática significa "CFOP de entrada não existe na tabela de saída". Simplesmente uma
  consequência do escopo já declarado ("NFe de entrada de terceiros — fica pra depois"),
  mas vale deixar registrado: qualquer nota com CFOP começando em 1/2/3 vai ser rejeitada
  hoje, sem mensagem clara do motivo. Corrigir (`tpNF` derivado do primeiro dígito do
  CFOP, ou validação prévia bloqueando CFOP de entrada) quando entrada entrar em escopo.

## O que falta (próximas rodadas)

- Carta de correção, inutilização de numeração (cancelamento já implementado e validado
  — ver tabela acima).
- Validar/bloquear CFOP de entrada antes de montar o XML (ver "tpNF sempre 1" acima).
- Outros UFs além de SP.
- Crédito de ICMS do Simples de verdade (portar `calculacsosn` do Axial).
- Frete/transportador em `NotaSaida`.
- Diferimento/devolução de tributo no bloco IBS/CBS.
- Criptografia da senha do certificado.
- Ver também `docs/REFORMA-TRIBUTARIA-IBS-CBS.md` (devolução refletindo a operação
  original, backfill de `Produto.classTrib`).
