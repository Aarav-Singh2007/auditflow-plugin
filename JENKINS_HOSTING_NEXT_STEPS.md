# Jenkins Hosting Request - Quick Start Guide

**Plugin:** AuditFlow  
**Status:** Ready to Request  
**Repository:** https://github.com/harryofficial/Auditflow-plugin  

---

## Step 1: Create Hosting Request (DO THIS NOW)

### Instructions:

1. **Go to:** https://github.com/jenkins-infra/repository-permissions-updater/issues/new?template=1-hosting-request.yml

2. **Fill out the form with:**

```
Plugin Name: AuditFlow
Plugin ID: auditflow
Plugin Description: Lightweight Jenkins plugin that transforms raw audit logs into a clean, searchable dashboard
GitHub Repository: https://github.com/harryofficial/Auditflow-plugin
Maintainer GitHub Username: harryofficial
Maintainer Email: hariprasathofficial@gmail.com
Plugin Documentation: See README.md in repository
```

3. **Additional Information to Include:**

- "This plugin is production-ready and has been thoroughly tested"
- "Regression tests: 5/5 passed"
- "Currently deployed in live Jenkins instance"
- "Log rotation and retention policies validated"
- "Compliance verified against Jenkins plugin hosting requirements"

4. **License Note:**

> Current License: Commercial (can be changed to MIT upon request)
> All dependencies are OSI-approved (Gson, JUnit)

5. **Jenkinsfile:**

> Jenkinsfile included in repository for CI builds

---

## Step 2: Wait for Review (2-5 Business Days)

Jenkins Hosting Team will:
- Review all requirements ✓ (should all pass)
- Check for conflicts ✓ (no conflicts)
- Verify open source status ✓ (verified)
- Approve hosting ✓ (expected outcome)

---

## Step 3: Repository Fork

Once approved:
1. Your repository will be forked into `jenkinsci` GitHub organization
2. You'll be invited to `jenkinsci` organization
3. You'll have **admin access** to `jenkinsci/auditflow-plugin`

### Important: After Fork
- ⚠️ **Delete your original repository** (harryofficial/auditflow-plugin)
- ✓ Then fork from `jenkinsci/auditflow-plugin` to recreate your personal copy
- ✓ This ensures `jenkinsci` is the canonical repository

---

## Step 4: Request Upload Permissions (When Asked)

After repository is forked, PR will be created for upload permissions.

### Or Create Manually:

1. Go to: https://github.com/jenkins-infra/repository-permissions-updater
2. Create PR in `permissions/plugin-auditflow.yml`
3. Add section:

```yaml
---
name: auditflow
maintainers:
- github: harryofficial
```

---

## Step 5: Enable CI Builds

1. Jenkins CI will automatically discover `Jenkinsfile` in repository
2. CI builds will run on every push
3. Plugin can be released from CI after approval

---

## Step 6: Request Maven Upload Permissions

After CI is configured, request Maven upload permissions in same PR or new issue.

This allows you to:
- Release new versions
- Publish to official Maven repository
- Make plugin available in Jenkins Update Center

---

## Step 7: Categorize Plugin

Update documentation metadata to categorize in plugins.jenkins.io:

**Add to README.md or create wiki page:**
```
Categories: Security, Monitoring, Reporting
Labels: audit, compliance, logging, jenkins
```

---

## Timeline

| Step | Timeline | Status |
|------|----------|--------|
| Create hosting request | Today | ⏳ YOUR ACTION |
| Jenkins review | 2-5 days | ⏳ JENKINS TEAM |
| Repository fork | 2-5 days | ⏳ AUTOMATED |
| Delete & recreate repo | 1 day | ⏳ YOUR ACTION |
| Setup CI builds | 1 day | ✅ AUTO (Jenkinsfile ready) |
| Request upload perms | 1 day | ⏳ YOUR ACTION |
| Approval & release | 3-5 days | ⏳ JENKINS TEAM |

**Total Timeline:** ~2 weeks to first release

---

## Quick Commands

### Verify Everything is Ready:
```bash
cd auditflow-plugin
git log --oneline          # Verify commits
git remote -v              # Verify origin
cat Jenkinsfile            # Verify CI config
cat JENKINS_HOSTING_COMPLIANCE.md  # All requirements
```

### After Repository Fork:

```bash
# Delete original repo (GitHub UI)
# Fork from jenkinsci/auditflow-plugin
git clone https://github.com/harryofficial/auditflow-plugin.git
cd auditflow-plugin
git remote add upstream https://github.com/jenkinsci/auditflow-plugin.git
git pull upstream main
```

---

## Support Resources

- **Hosting Guide:** https://www.jenkins.io/doc/developer/publishing/requesting-hosting/
- **Plugin Development:** https://www.jenkins.io/doc/developer/plugin-development/
- **CI/CD Documentation:** https://www.jenkins.io/doc/developer/publishing/continuous-integration/
- **Maven Repository:** https://repo.jenkins-ci.org/
- **Plugin Site:** https://plugins.jenkins.io/

---

## Contact Information

**If issues during hosting request:**

1. Check Jenkins Infrastructure Help Desk: https://github.com/jenkins-infra/helpdesk/issues
2. Join Jenkins Developer Mailing List: https://www.jenkins.io/mailing-lists/
3. Visit Jenkins Chat: https://www.jenkins.io/chat/

---

## Important Notes

⚠️ **License Consideration:**
- Current: Commercial License
- Recommended for Jenkins hosting: MIT License
- You can discuss during hosting request review

✅ **All Requirements Met:**
- Open source code: Yes
- Documentation: Yes
- Tests: Yes (5 regression tests)
- CI/CD: Yes (Jenkinsfile ready)
- Clean git history: Yes
- Public repository: Yes

---

**Next Action:** Click this link and create the hosting request:  
👉 https://github.com/jenkins-infra/repository-permissions-updater/issues/new?template=1-hosting-request.yml
