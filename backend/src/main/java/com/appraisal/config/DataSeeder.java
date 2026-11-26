package com.appraisal.config;

import com.appraisal.domain.*;
import com.appraisal.domain.Enums.*;
import com.appraisal.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

/**
 * First start only: loads the question bank and scoring rules extracted from
 * Performance_Appraisal_All_Teams.xlsx (seed/seed-data.json) and creates the first admin account.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TeamRepo teams;
    private final LevelRepo levels;
    private final CategoryRepo categories;
    private final QuestionRepo questions;
    private final ScoringSettingsRepo settings;
    private final RatingBandRepo bands;
    private final ScaleLabelRepo scale;
    private final PickListItemRepo pickLists;
    private final AppUserRepo users;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;

    @Value("${app.seed.admin-username:admin}")
    private String adminUsername;
    @Value("${app.seed.admin-password:admin123}")
    private String adminPassword;
    @Value("${app.seed.demo-users:false}")
    private boolean demoUsers;
    /** ADMIN_PASSWORD_RESET=true: on startup, set the admin account's password to ADMIN_PASSWORD (recovery). */
    @Value("${app.seed.admin-password-reset:false}")
    private boolean resetAdminPassword;

    public DataSeeder(TeamRepo teams, LevelRepo levels, CategoryRepo categories, QuestionRepo questions,
                      ScoringSettingsRepo settings, RatingBandRepo bands, ScaleLabelRepo scale, PickListItemRepo pickLists,
                      AppUserRepo users, PasswordEncoder encoder, ObjectMapper mapper) {
        this.teams = teams;
        this.levels = levels;
        this.categories = categories;
        this.questions = questions;
        this.settings = settings;
        this.bands = bands;
        this.scale = scale;
        this.pickLists = pickLists;
        this.users = users;
        this.encoder = encoder;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (teams.count() == 0 && levels.count() == 0) {
            try (InputStream in = new ClassPathResource("seed/seed-data.json").getInputStream()) {
                seed(mapper.readTree(in));
            }
        }
        String username = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();
        String password = adminPassword == null ? "" : adminPassword.trim();
        if (password.isEmpty()) {
            password = "admin123";
            log.warn("ADMIN_PASSWORD is empty; using the default 'admin123'. Change it after signing in.");
        }
        if (users.count() == 0) {
            AppUser admin = new AppUser();
            admin.setUsername(username);
            admin.setFullName("Administrator");
            admin.setPasswordHash(encoder.encode(password));
            admin.setAppraised(false);
            admin.getRoles().add(Role.ADMIN);
            users.save(admin);
            log.warn("Created admin user '{}'. Change its password after the first sign-in.", username);
            if (demoUsers) createDemoUsers();
        } else if (resetAdminPassword) {
            AppUser admin = users.findByUsernameIgnoreCase(username).orElseGet(() -> {
                AppUser u = new AppUser();
                u.setUsername(username);
                u.setFullName("Administrator");
                u.setAppraised(false);
                return u;
            });
            admin.setPasswordHash(encoder.encode(password));
            admin.setActive(true);
            admin.getRoles().add(Role.ADMIN);
            users.save(admin);
            log.warn("ADMIN_PASSWORD_RESET: password of admin user '{}' was reset from ADMIN_PASSWORD. Remove ADMIN_PASSWORD_RESET now.", username);
        }
    }

    private void seed(JsonNode root) {
        Map<String, Level> levelByName = new LinkedHashMap<>();
        int i = 1;
        for (JsonNode l : root.get("levels")) {
            Level lv = new Level();
            lv.setName(l.get("name").asText());
            lv.setSortOrder(i++);
            lv.setSkipTlReview(l.get("skipTlReview").asBoolean());
            levelByName.put(lv.getName(), levels.save(lv));
        }

        JsonNode s = root.get("settings");
        ScoringSettings ss = new ScoringSettings();
        ss.setCommonWeight(s.get("commonWeight").asDouble());
        ss.setRoleWeight(s.get("roleWeight").asDouble());
        ss.setSelfWeight(s.get("selfWeight").asDouble());
        ss.setPeerWeight(s.get("peerWeight").asDouble());
        ss.setTlWeight(s.get("tlWeight").asDouble());
        ss.setManagerWeight(s.get("managerWeight").asDouble());
        settings.save(ss);

        for (JsonNode b : root.get("ratingBands")) {
            RatingBand rb = new RatingBand();
            rb.setLabel(b.get("label").asText());
            rb.setMinScore(b.get("minScore").asDouble());
            bands.save(rb);
        }
        for (JsonNode b : root.get("scaleLabels")) {
            ScaleLabel sl = new ScaleLabel();
            sl.setValue(b.get("value").asInt());
            sl.setLabel(b.get("label").asText());
            scale.save(sl);
        }
        root.get("pickLists").fields().forEachRemaining(e -> {
            int o = 1;
            for (JsonNode item : e.getValue()) {
                PickListItem p = new PickListItem();
                p.setListType(PickListType.valueOf(e.getKey()));
                p.setLabel(item.asText());
                p.setSortOrder(o++);
                pickLists.save(p);
            }
        });

        int c = 1;
        for (JsonNode cat : root.get("commonCategories")) saveCategory(cat, Block.COMMON, null, c++, levelByName);
        int t = 1;
        for (JsonNode tn : root.get("teams")) {
            Team team = new Team();
            team.setCode(tn.get("code").asText());
            team.setName(tn.get("name").asText());
            team.setSortOrder(t++);
            team = teams.save(team);
            int rc = 1;
            for (JsonNode cat : tn.get("roleCategories")) saveCategory(cat, Block.ROLE, team, rc++, levelByName);
        }
        log.info("Seeded question bank: {} teams, {} categories, {} questions", teams.count(), categories.count(), questions.count());
    }

    private void saveCategory(JsonNode node, Block block, Team team, int order, Map<String, Level> levelByName) {
        Category c = new Category();
        c.setName(node.get("name").asText());
        c.setBlock(block);
        c.setTeam(team);
        c.setSortOrder(order);
        node.get("points").fields().forEachRemaining(e -> c.getLevelPoints().put(levelByName.get(e.getKey()).getId(), e.getValue().asDouble()));
        Category saved = categories.save(c);
        int q = 1;
        for (JsonNode qn : node.get("questions")) {
            Question question = new Question();
            question.setCategory(saved);
            question.setText(qn.get("text").asText());
            question.setWeight(qn.get("weight").asDouble());
            question.setReviewedBy(ReviewedBy.valueOf(qn.get("reviewedBy").asText()));
            String g = qn.path("guidance").asText("");
            question.setGuidance(g.isBlank() ? null : g);
            question.setSortOrder(q++);
            questions.save(question);
        }
    }

    /** Sample people for trying the app (app.seed.demo-users=true). Password for all: password123 */
    private void createDemoUsers() {
        Team ca = teams.findByCode("CA").orElse(null);
        if (ca == null) return;
        Level jr = levels.findByName("Junior").orElse(null), sr = levels.findByName("Senior").orElse(null),
                tl = levels.findByName("Team Lead").orElse(null);
        demo("manager", "Maya Manager", ca, null, false, Role.MANAGER);
        demo("lead", "Leo Lead", ca, tl, true, Role.TL, Role.EMPLOYEE);
        demo("sara", "Sara Senior", ca, sr, true, Role.EMPLOYEE);
        demo("jay", "Jay Junior", ca, jr, true, Role.EMPLOYEE);
        log.warn("Created demo users manager / lead / sara / jay with password 'password123'");
    }

    private void demo(String username, String name, Team team, Level level, boolean appraised, Role... roles) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(name);
        u.setPasswordHash(encoder.encode("password123"));
        u.setTeam(team);
        u.setLevel(level);
        u.setAppraised(appraised);
        u.getRoles().addAll(Arrays.asList(roles));
        users.save(u);
    }
}
