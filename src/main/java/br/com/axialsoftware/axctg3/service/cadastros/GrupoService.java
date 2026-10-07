package br.com.axialsoftware.axctg3.service.cadastros;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.security.AdminGrupoRole;
import br.com.axialsoftware.axctg3.security.GerenteCadastrosRole;
import br.com.axialsoftware.axctg3.security.GerenteContabilRole;
import br.com.axialsoftware.axctg3.security.GerenteFinanceiroRole;
import br.com.axialsoftware.axctg3.security.GerenteFiscalRole;
import br.com.axialsoftware.axctg3.security.UiMinimalRole;
import io.jmix.core.DataManager;
import io.jmix.core.EntitySet;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Cadastro de um grupo (cliente da Axial) já com a primeira empresa e o administrador do
 * grupo — tudo num único save, para não sobrar grupo sem empresa ou sem quem o administre.
 */
@Service
public class GrupoService {

    static final String SEQUENCIA_CODIGO_EMPRESA = "empresa_codigo";

    /** Papéis do administrador criado junto com o grupo. */
    static final List<String> PAPEIS_ADMIN_GRUPO = List.of(
            AdminGrupoRole.CODE, UiMinimalRole.CODE,
            GerenteCadastrosRole.CODE, GerenteContabilRole.CODE,
            GerenteFinanceiroRole.CODE, GerenteFiscalRole.CODE);

    public record PrimeiraEmpresaEAdmin(String nomeEmpresa, String apelidoEmpresa, String cnpjEmpresa,
                                        String usernameAdmin, String senhaAdmin) {
    }

    private final DataManager dataManager;
    private final UnconstrainedDataManager unconstrainedDataManager;
    private final Sequences sequences;
    private final PasswordEncoder passwordEncoder;

    public GrupoService(DataManager dataManager, UnconstrainedDataManager unconstrainedDataManager,
                        Sequences sequences, PasswordEncoder passwordEncoder) {
        this.dataManager = dataManager;
        this.unconstrainedDataManager = unconstrainedDataManager;
        this.sequences = sequences;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Grava o grupo (já presente em {@code saveContext}, vindo da tela) junto com a
     * primeira empresa, o administrador e os papéis dele.
     */
    public EntitySet criarGrupo(SaveContext saveContext, Grupo grupo, PrimeiraEmpresaEAdmin dados) {
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(proximoCodigoEmpresa());
        empresa.setCodGrupo(grupo.getCodigo());
        empresa.setNome(dados.nomeEmpresa());
        empresa.setApelido(dados.apelidoEmpresa());
        empresa.setCnpj(dados.cnpjEmpresa());

        LocalDate hoje = LocalDate.now();
        User admin = dataManager.create(User.class);
        admin.setUsername(dados.usernameAdmin());
        admin.setPassword(passwordEncoder.encode(dados.senhaAdmin()));
        admin.setPasswordChangeRequired(true);
        admin.setActive(true);
        admin.setCodGrupo(grupo.getCodigo());
        admin.setCodEmpresa(empresa.getCodigo());
        admin.setAnoContabil(hoje.getYear());
        admin.setMesContabil(hoje.getMonthValue());
        admin.setAnoFiscal(hoje.getYear());
        admin.setMesFiscal(hoje.getMonthValue());

        saveContext.saving(grupo, empresa, admin);
        for (String papel : PAPEIS_ADMIN_GRUPO) {
            RoleAssignmentEntity atribuicao = dataManager.create(RoleAssignmentEntity.class);
            atribuicao.setUsername(admin.getUsername());
            atribuicao.setRoleCode(papel);
            atribuicao.setRoleType(RoleAssignmentRoleType.RESOURCE);
            saveContext.saving(atribuicao);
        }
        return dataManager.save(saveContext);
    }

    public boolean usernameEmUso(String username) {
        return unconstrainedDataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .optional()
                .isPresent();
    }

    public boolean codigoGrupoEmUso(Integer codigo) {
        return unconstrainedDataManager.load(Grupo.class)
                .query("select g from Grupo g where g.codigo = :codigo")
                .parameter("codigo", codigo)
                .optional()
                .isPresent();
    }

    /**
     * Próximo valor da Sequence global que ainda não é código de nenhuma empresa — nem das
     * que já existiam antes da Sequence, nem das excluídas (soft delete), cujos
     * lançamentos, títulos etc. continuam gravados com aquele codEmpresa.
     */
    public int proximoCodigoEmpresa() {
        while (true) {
            int codigo = (int) sequences.createNextValue(Sequence.withName(SEQUENCIA_CODIGO_EMPRESA));
            long existentes = unconstrainedDataManager.loadValue(
                            "select count(e) from Empresa e where e.codigo = :codigo", Long.class)
                    .hint(PersistenceHints.SOFT_DELETION, false)
                    .parameter("codigo", codigo)
                    .one();
            if (existentes == 0) {
                return codigo;
            }
        }
    }
}
