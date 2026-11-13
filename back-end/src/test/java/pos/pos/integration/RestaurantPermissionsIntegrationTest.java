package pos.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.user.entity.User;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Real JWTs, security filters, services and PostgreSQL: no mocked authorization. */
class RestaurantPermissionsIntegrationTest extends AbstractPosApiIntegrationTest {
    @Autowired PermissionRepository permissions;
    record Request(String method, String path, Object body) {}

    private Map<String,Object> restaurantBody(World w) {
        var r=restaurantRepository.findById(w.restaurantId()).orElseThrow();
        var body=new HashMap<String,Object>(Map.of("name",r.getName(),"legalName",r.getLegalName(),"code",r.getCode(),"slug",r.getSlug(),
                "currency",r.getCurrency(),"timezone",r.getTimezone(),"ownerUserId",r.getOwnerId(),"isActive",true,"status","ACTIVE","expectedVersion",r.getVersion()));
        body.put("email",r.getEmail());body.put("phone",r.getPhone());
        return body;
    }
    private Map<String,Object> branchBody(World w) {
        return Map.of("name",w.branch().getName(),"code",w.branch().getCode(),"isActive",true,"status","ACTIVE","expectedVersion",branchRepository.findById(w.branchId()).orElseThrow().getVersion());
    }
    private Map<String,Object> address() { return Map.of("addressType","PHYSICAL","country","Albania","city","Tirana","streetLine1","Main 1","isPrimary",true); }
    private Map<String,Object> contact() { return Map.of("contactType","GENERAL","fullName","Test Contact","email","contact@example.invalid","isPrimary",true); }
    private Map<String,Object> tax() { return Map.of("country","Albania","vatNumber","TEST123","isDefault",true); }
    private Map<String,Object> owner() { return Map.of("firstName","Owner","lastName","Tester","email","owner"+next()+"@example.invalid","username","owner"+next()); }
    private Map<String,Object> create() { return Map.of("name","New "+next(),"legalName","New LLC","currency","EUR","timezone","Europe/Tirane","email","restaurant@example.invalid","phone","+355691234567"); }

    private List<Request> routes(World w, String admin) throws Exception {
        String r="/restaurants/"+w.restaurantId(), b=r+"/branches/"+w.branchId();
        put(r+"/branding").as(admin).body(Map.of("primaryColor","#123456")).expect(200);
        String ra=post(r+"/addresses").as(admin).body(address()).expect(201).get("id").asText();
        String rc=post(r+"/contacts").as(admin).body(contact()).expect(201).get("id").asText();
        String rt=post(r+"/tax-profiles").as(admin).body(tax()).expect(201).get("id").asText();
        String ba=post(b+"/addresses").as(admin).body(address()).expect(201).get("id").asText();
        String bc=post(b+"/contacts").as(admin).body(contact()).expect(201).get("id").asText();
        var list=new ArrayList<Request>();
        for (String path: List.of(r,r+"/summary", "/restaurants/by-slug/"+w.restaurant().getSlug(),r+"/branches",r+"/branches/cards",b,b+"/removal-check",r+"/branding",r+"/currency/changes",r+"/owner/transfers",r+"/owner/candidates")) list.add(new Request("GET",path,null));
        for(var resource: List.of(new Object[]{r+"/addresses",ra,address(),"primary"},new Object[]{r+"/contacts",rc,contact(),"primary"},new Object[]{r+"/tax-profiles",rt,tax(),"default"},new Object[]{b+"/addresses",ba,address(),"primary"},new Object[]{b+"/contacts",bc,contact(),"primary"})) {
            String path=(String)resource[0], id=(String)resource[1];
            list.add(new Request("GET",path,null));
            list.add(new Request("POST",path,resource[2]));
            list.add(new Request("PUT",path+"/"+id,resource[2]));
            list.add(new Request("PATCH",path+"/"+id+"/"+resource[3],null));
            list.add(new Request("DELETE",path+"/"+id,null));
        }
        list.add(new Request("PATCH","/restaurants/registrations/"+w.restaurantId()+"/review",Map.of("decision","REJECT","rejectionReason","Test")));
        list.add(new Request("PUT",r,restaurantBody(w)));
        list.add(new Request("PATCH",r+"/status",Map.of("isActive",false,"status","SUSPENDED")));
        list.add(new Request("POST",r+"/branches",Map.of("name","Other","code","OTHER")));
        list.add(new Request("PUT",b,branchBody(w)));
        list.add(new Request("PATCH",b+"/status",Map.of("isActive",false,"status","INACTIVE")));
        list.add(new Request("POST",r+"/owner",owner()));
        list.add(new Request("PUT",r+"/owner",Map.of("firstName","Owner","lastName","Tester","email",w.owner().getEmail())));
        list.add(new Request("POST",r+"/owner/invite",null));
        list.add(new Request("POST",r+"/owner/transfer",Map.of("newOwnerUserId",w.owner().getId(),"expectedVersion",0)));
        list.add(new Request("PUT",r+"/branding",Map.of("primaryColor","#123456")));
        for(String suffix: List.of("currency/preview","currency/change")) list.add(new Request("POST",r+"/"+suffix,Map.of("newCurrency","USD","rate",1.1,"roundingStep",0.01)));
        list.add(new Request("POST",r+"/timezone/preview",Map.of("timezone","Europe/Berlin")));
        list.add(new Request("POST",r+"/timezone/change",Map.of("timezone","Europe/Berlin","confirmed",true,"keepBookingTimes",true)));
        for(String path: List.of(r+"/cover",b+"/cover",r+"/branding/logo")) {
            list.add(new Request("UPLOAD",path,null)); list.add(new Request("DELETE",path,null));
        }
        list.add(new Request("DELETE",b,null));list.add(new Request("DELETE",r,null));
        return list;
    }
    private int invoke(Request request, String token, Map<String,String> headers) throws Exception {
        if(request.method().equals("UPLOAD")) {
            var builder=MockMvcRequestBuilders.multipart(request.path()).file(new MockMultipartFile("file","test.png","image/png",new byte[]{1,2,3}));
            builder.with(req->{req.setMethod("PUT");return req;});
            if(token!=null) builder.header("Authorization","Bearer "+token);
            headers.forEach(builder::header);
            return mockMvc.perform(builder).andReturn().getResponse().getStatus();
        }
        var c=call(HttpMethod.valueOf(request.method()),request.path());
        if(token!=null)c.as(token);
        if(request.body()!=null)c.body(request.body());
        headers.forEach(c::header);
        return c.send().status();
    }
    @Test void everyRestaurantActionRejectsForeignOwnerEvenWithForgedContextHeaders() throws Exception {
        World a=newWorld("permission-a"),b=newWorld("permission-b");
        var requests=routes(a,token(superAdmin()));
        for(var request:requests) {
            assertThat(invoke(request,b.ownerToken(),Map.of("X-Restaurant-Id",a.restaurantId().toString(),"X-Branch-Id",a.branchId().toString())))
                    .as(request.toString()).isIn(403,404);
        }
        assertThat(restaurantRepository.findByIdAndDeletedAtIsNull(a.restaurantId())).isPresent();
        System.out.println("Restaurant isolation routes checked: "+requests.size());
    }
    @Test void everyActionRequiresAuthenticationAndTheRelevantPermission() throws Exception {
        World w=newWorld("permission-none");String admin=token(superAdmin());
        User co=staff(w.restaurant(),w.branch(),"co-denied","CO_SUPER_ADMIN");String coToken=token(co);
        put("/co-super-admins/{id}/permissions",co.getId()).as(admin).body(Map.of("grantedCodes",List.of())).expect(200);
        for(var request:routes(w,admin)) {
            assertThat(invoke(request,null,Map.of())).as("anonymous "+request).isEqualTo(401);
            assertThat(invoke(request,coToken,Map.of())).as("denied "+request).isEqualTo(403);
        }
        for(var request:List.of(new Request("POST","/restaurants",create()),new Request("PUT","/restaurants/order",Map.of("restaurantIds",List.of(w.restaurantId()))),new Request("GET","/restaurants/cards",null),new Request("GET","/restaurants",null)))
            assertThat(invoke(request,coToken,Map.of())).as(request.toString()).isEqualTo(403);
    }
    @Test void listFiltersCannotExposeAnotherRestaurantAndReadOnlyPlatformAccessStillWorks() throws Exception {
        World a=newWorld("list-scope-a"),b=newWorld("list-scope-b");String admin=token(superAdmin());
        for(String path:List.of("/restaurants", "/restaurants?ownerUserId="+b.owner().getId(), "/restaurants?search="+b.restaurant().getCode())) {
            String json=get(path).as(a.ownerToken()).expect(200).toString();
            assertThat(json).doesNotContain(b.restaurantId().toString());
        }
        get("/restaurants/cards").as(a.ownerToken()).expect(403);
        post("/restaurants").as(a.ownerToken()).body(create()).expect(403);
        put("/restaurants/order").as(a.ownerToken()).body(Map.of("restaurantIds",List.of(a.restaurantId(),b.restaurantId()))).expect(403);
        User co=staff(a.restaurant(),a.branch(),"co-read","CO_SUPER_ADMIN");String t=token(co);
        put("/co-super-admins/{id}/permissions",co.getId()).as(admin).body(Map.of("grantedCodes",List.of("RESTAURANTS_READ"))).expect(200);
        for(var request:routes(a,admin))
            assertThat(invoke(request,t,Map.of())).as("read-only "+request).isEqualTo(request.method().equals("GET")?200:403);
    }
    @Test void ownerCanEditContactDetailsButCannotUsePlatformActions() throws Exception {
        World w=newWorld("permission-owner");String r="/restaurants/"+w.restaurantId();
        var permitted=new HashMap<>(restaurantBody(w));permitted.put("phone","+355691234567");
        put(r).as(w.ownerToken()).body(permitted).expect(200);
        for(String field:List.of("name","legalName","code","slug")) {
            var denied=new HashMap<>(permitted);denied.put(field,"changed");
            put(r).as(w.ownerToken()).body(denied).expect(403);
        }
        for(var request:routes(w,token(superAdmin()))) {
            boolean platform=request.path().contains("/tax-profiles")&&!request.method().equals("GET")
                    || request.path().contains("/currency/")&&request.method().equals("POST")
                    || request.path().contains("/timezone/") || request.path().contains("/owner")
                    || request.path().endsWith("/status") || request.path().endsWith("/cover")
                    || request.path().endsWith("/branches")&&request.method().equals("POST")
                    || request.method().equals("DELETE")&&(request.path().equals(r)||request.path().equals(r+"/branches/"+w.branchId()));
            if(platform) assertThat(invoke(request,w.ownerToken(),Map.of())).as(request.toString()).isEqualTo(403);
        }
    }
    @Test void childResourcesCannotBeUsedUnderAnotherBranchOrRestaurant() throws Exception {
        World a=newWorld("permission-parent-a"),b=newWorld("permission-parent-b");String admin=token(superAdmin());
        String ar="/restaurants/"+a.restaurantId(), br="/restaurants/"+b.restaurantId();
        String contactId=post(br+"/branches/"+b.branchId()+"/contacts").as(admin).body(contact()).expect(201).get("id").asText();
        for(String path:List.of(ar+"/branches/"+b.branchId(),ar+"/branches/"+b.branchId()+"/removal-check",ar+"/branches/"+b.branchId()+"/contacts")) get(path).as(admin).expect(404);
        put(ar+"/branches/"+a.branchId()+"/contacts/"+contactId).as(admin).body(contact()).expect(404);
        var wrongManager=new HashMap<>(branchBody(a));wrongManager.put("managerUserId",b.owner().getId());
        put(ar+"/branches/"+a.branchId()).as(admin).body(wrongManager).expect(400);
    }
    @Test void everyBranchRouteRejectsAMismatchedRestaurantParent() throws Exception {
        World a=newWorld("branch-parent-a"), b=newWorld("branch-parent-b");String admin=token(superAdmin());
        String original="/restaurants/"+a.restaurantId()+"/branches/"+a.branchId();
        String forged="/restaurants/"+a.restaurantId()+"/branches/"+b.branchId();
        int checked=0;
        for(var request:routes(a,admin)) if(request.path().startsWith(original)) {
            var wrong=new Request(request.method(),request.path().replace(original,forged),request.body());
            // Invalid upload content must not reveal or modify a different restaurant's branch.
            assertThat(invoke(wrong,admin,Map.of())).as(wrong.toString()).isEqualTo(404);
            checked++;
        }
        System.out.println("Branch parent isolation routes checked: "+checked);
    }
    @Test void childIdsCannotBeReusedAcrossBranchesOrRestaurantScopes() throws Exception {
        World w=newWorld("branch-child");String admin=token(superAdmin());String r="/restaurants/"+w.restaurantId();
        String b1=r+"/branches/"+w.branchId();
        String other=post(r+"/branches").as(admin).body(Map.of("name","Second","code","SECOND")).expect(201).get("id").asText();
        String b2=r+"/branches/"+other;
        World foreign=newWorld("foreign-child");
        String fr="/restaurants/"+foreign.restaurantId(), fb=fr+"/branches/"+foreign.branchId();
        for(String resource:List.of("addresses","contacts")) {
            Object body=resource.equals("addresses")?address():contact();
            String child=post(b1+"/"+resource).as(admin).body(body).expect(201).get("id").asText();
            for(String parent:List.of(b2,r,fr,fb)) {
                put(parent+"/"+resource+"/"+child).as(admin).body(body).expect(404);
                patch(parent+"/"+resource+"/"+child+"/primary").as(admin).expect(404);
                delete(parent+"/"+resource+"/"+child).as(admin).expect(404);
            }
            assertThat(get(b1+"/"+resource).as(admin).expect(200).toString()).contains(child);
        }
    }
    @Test void restaurantChildrenStayBoundToTheirRestaurant() throws Exception {
        World a=newWorld("restaurant-child-a"),b=newWorld("restaurant-child-b");String admin=token(superAdmin());
        String ar="/restaurants/"+a.restaurantId(),br="/restaurants/"+b.restaurantId();
        for(String resource:List.of("addresses","contacts","tax-profiles")) {
            Object body=resource.equals("addresses")?address():resource.equals("contacts")?contact():tax();
            String child=post(ar+"/"+resource).as(admin).body(body).expect(201).get("id").asText();
            put(br+"/"+resource+"/"+child).as(admin).body(body).expect(404);
            patch(br+"/"+resource+"/"+child+(resource.equals("tax-profiles")?"/default":"/primary")).as(admin).expect(404);
            delete(br+"/"+resource+"/"+child).as(admin).expect(404);
            assertThat(get(ar+"/"+resource).as(admin).expect(200).toString()).contains(child);
        }
    }
    @Test void coSuperAdminCannotReadOrManageProtectedAccountsOrDeleteSomeoneElsesStaff() throws Exception {
        World w=newWorld("protected-users");User co=staff(w.restaurant(),w.branch(),"co-actor","CO_SUPER_ADMIN");
        User peer=staff(w.restaurant(),w.branch(),"co-peer","CO_SUPER_ADMIN");String t=token(co);
        for(User target:List.of(superAdmin(),peer)) {
            get("/users/{id}",target.getId()).as(t).expect(403);
            get("/users/{id}/roles",target.getId()).as(t).expect(403);
            put("/users/{id}",target.getId()).as(t).body(Map.of("firstName","Changed","lastName","User","isActive",false)).expect(403);
            delete("/users/{id}",target.getId()).as(t).expect(403);
            post("/users/{id}/reset-password",target.getId()).as(t).expect(403);
        }
        delete("/users/{id}",w.owner().getId()).as(t).expect(403);
    }
    @Test void permissionChangesApplyToExistingTokenAndProtectPlatformAccounts() throws Exception {
        World w=newWorld("permission-revoke");String admin=token(superAdmin());
        User co=staff(w.restaurant(),w.branch(),"co-live","CO_SUPER_ADMIN");String t=token(co);
        var codes=new HashSet<>(permissions.findCodesByRoleIds(List.of(roleRepository.findByCode("CO_SUPER_ADMIN").orElseThrow().getId())));
        put("/restaurants/{id}",w.restaurantId()).as(t).body(restaurantBody(w)).expect(200);
        codes.remove("RESTAURANTS_UPDATE");
        put("/co-super-admins/{id}/permissions",co.getId()).as(admin).body(Map.of("grantedCodes",codes)).expect(200);
        put("/restaurants/{id}",w.restaurantId()).as(t).body(restaurantBody(w)).expect(403);
        JsonNode me=get("/auth/me").as(t).expect(200);
        assertThat(me.get("permissions").toString()).doesNotContain("RESTAURANTS_UPDATE");
        codes.add("RESTAURANTS_UPDATE");
        put("/co-super-admins/{id}/permissions",co.getId()).as(admin).body(Map.of("grantedCodes",codes)).expect(200);
        put("/restaurants/{id}",w.restaurantId()).as(t).body(restaurantBody(w)).expect(200);
        put("/co-super-admins/{id}/permissions",co.getId()).as(t).body(Map.of("grantedCodes",codes)).expect(403);
        delete("/users/{id}",superAdmin().getId()).as(t).expect(403);
    }
    @Test void applicationStatusNeedsTheCodeAndOnlyPlatformAdminsReview() throws Exception {
        World w=newWorld("private-registration");
        // There is no status lookup by restaurant id any more: only the applicant's private code answers.
        assertThat(get("/restaurants/registrations/{id}",w.restaurantId()).send().status()).isIn(404,405);
        post("/restaurants/registrations/status").body(Map.of("code","00000-00000-00000-00000")).expect(404);
        patch("/restaurants/registrations/{id}/review",w.restaurantId()).as(w.ownerToken())
                .body(Map.of("decision","REJECT","rejectionReason","Test")).expect(403);
        get("/platform/restaurant-applications").as(w.ownerToken()).expect(403);
        get("/platform/restaurant-applications").expect(401);
    }
    @Test void actingHeadersMustIdentifyAnExistingRestaurantAndMatchingBranch() throws Exception {
        World a=newWorld("context-a"),b=newWorld("context-b");String admin=token(superAdmin());
        get("/auth/me").as(admin).header("X-Restaurant-Id",a.restaurantId().toString()).header("X-Branch-Id",a.branchId().toString()).expect(200);
        get("/auth/me").as(admin).header("X-Restaurant-Id",a.restaurantId().toString()).header("X-Branch-Id",b.branchId().toString()).expect(403);
        get("/auth/me").as(admin).header("X-Branch-Id",a.branchId().toString()).expect(403);
        get("/auth/me").as(admin).header("X-Restaurant-Id","bad-id").expect(400);
        get("/auth/me").as(admin).header("X-Restaurant-Id",UUID.randomUUID().toString()).expect(403);
    }
    @Test void refreshWorksInConfiguredSchemaAndExpiredSessionCannotWrite() throws Exception {
        World w=newWorld("permission-refresh");
        var login=post("/auth/device/login").body(Map.of("identifier",w.owner().getUsername(),"password",PASSWORD)).expect(200);
        String access=login.get("accessToken").asText(), refresh=login.get("refreshToken").asText();
        var renewed=post("/auth/device/refresh").body(Map.of("refreshToken",refresh)).expect(200);
        get("/auth/me").as(renewed.get("accessToken").asText()).expect(200);
        // Explicit revocation is checked on every API request, including a dialog opened with the old token.
        jdbcTemplate.update("update pos_api_it.user_sessions set revoked = true, revoked_at = CURRENT_TIMESTAMP where user_id = ?",w.owner().getId());
        put("/restaurants/{id}",w.restaurantId()).as(access).body(restaurantBody(w)).expect(401);
    }
}
