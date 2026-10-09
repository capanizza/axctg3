Schemas oficiais da NFe 4.00 (pacote do Portal Nacional da NF-e com o grupo IBS/CBS da
Reforma Tributária, cópia de C:\tmp\Schemas\NFe de 2025-10). Usados só nos testes, para
validar o XML gerado pelo NfeXmlSerializer.

Única alteração em relação ao original: em leiauteNFe_v4.00.xsd, o ds:Signature do tipo
TNFe passou a minOccurs="0" (linha marcada com comentário "axctg3"), porque os testes
validam o XML antes da assinatura.
