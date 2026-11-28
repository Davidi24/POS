package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.*
import com.saporini.mobile_desktop.core.session.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import kotlin.test.*

class RestaurantNavigationHttpTest {
    @Test fun productionClientSendsNewContextAndRecoversFromAnUnavailableSelection()=runBlocking {
        assertTrue(System.getProperty("user.home").endsWith("test-home"))
        val s=SessionManager().apply {signIn(CurrentUserResponse(id="u",email="u@example.invalid",username="u",firstName="U",lastName="T",isActive=true,emailVerified=true,phoneVerified=false,roles=listOf("SUPER_ADMIN"),permissions=listOf("RESTAURANTS_READ")))}
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val seen=mutableListOf<Pair<String?,String?>>()
        var closed=false
        server.createContext("/"){x->
            seen+=x.requestHeaders.getFirst("X-Restaurant-Id") to x.requestHeaders.getFirst("X-Branch-Id")
            val status=if(closed)403 else 200
            if(closed)x.responseHeaders.add("X-POS-Context-Invalid","true")
            x.responseHeaders.add("Content-Type","application/json")
            val bytes="""{"message":"The branch is unavailable"}""".toByteArray()
            x.sendResponseHeaders(status,bytes.size.toLong());x.responseBody.use{it.write(bytes)}
        }
        server.start();TokenStore.save("access","refresh");val c=createHttpClient(s)
        val url="http://127.0.0.1:${server.address.port}/auth/me"
        try {
            s.enterRestaurant(ActingRestaurant("r","R","b","B"));c.get(url)
            s.enterRestaurant(ActingRestaurant("r2","R2","b2","B2"));c.get(url)
            closed=true;assertFailsWith<ApiException>{c.get(url)}
            assertNull(s.actingRestaurant.value);assertNotNull(s.contextError.value);assertTrue(s.isAuthenticated.value)
            closed=false;c.get(url)
            assertEquals(listOf("r" to "b","r2" to "b2","r2" to "b2",null to null),seen)
            assertEquals("refresh",TokenStore.refreshToken.value)
        }finally{c.close();server.stop(0);TokenStore.clear()}
    }
    @Test fun branchRepositoryLoadsAllPagesAndPropagatesLaterPageFailure()=runBlocking {
        val requested=mutableListOf<Int>();var fail=false
        val engine=MockEngine {request->
            val page=request.url.parameters["page"]?.toInt()?:-1;requested+=page
            if(fail&&page==1)respond("""{"message":"Offline"}""",HttpStatusCode.ServiceUnavailable,headersOf(HttpHeaders.ContentType,"application/json"))
            else respond(Json.encodeToString(PageDto(items=(page*100 until minOf((page+1)*100,121)).map{BranchDto("b$it","Branch $it")},page=page,size=100,totalElements=121,totalPages=2,hasNext=page==0)),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=HttpClient(engine){expectSuccess=true;install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        try{
            val repo=PlatformApi(client){"http://test"};assertEquals(121,repo.branches("r").size);assertEquals(listOf(0,1),requested)
            fail=true;assertFails {repo.branches("r")};Unit
        }finally{client.close()}
    }
    @Test fun removalChoicesReachTheServerAsAnExplicitDeleteBody()=runBlocking {
        TokenStore.clear()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val requests=mutableListOf<Pair<String,String>>()
        val plan=BranchRemovalPlanDto(canRemove=true,staff=2,protectedStaff=1,canDeactivateStaff=true,expectedVersion=7,previewToken="reviewed")
        server.createContext("/"){x->
            if(x.requestMethod=="GET") {
                val bytes=Json.encodeToString(BranchRemovalCheckDto(archive=plan)).toByteArray()
                x.responseHeaders.add("Content-Type","application/json");x.sendResponseHeaders(200,bytes.size.toLong());x.responseBody.use{it.write(bytes)}
            } else {
                requests+=x.requestURI.path to x.requestBody.bufferedReader().use{it.readText()}
                x.sendResponseHeaders(204,-1);x.close()
            }
        }
        server.start();val client=createHttpClient(SessionManager())
        try {
            val api=PlatformApi(client){"http://127.0.0.1:${server.address.port}"}
            assertEquals(plan,api.branchRemovalCheck("r","b").archive)
            for(action in listOf("KEEP","DEACTIVATE"))api.removeBranch("r","b",BranchRemovalRequestDto(action,7,"Main","reviewed"))
            assertEquals(2,requests.size)
            requests.forEachIndexed { index, (path,body) ->
                assertEquals("/restaurants/r/branches/b",path)
                assertEquals(BranchRemovalRequestDto(listOf("KEEP","DEACTIVATE")[index],7,"Main","reviewed"),Json.decodeFromString<BranchRemovalRequestDto>(body))
            }
        }finally{client.close();server.stop(0);TokenStore.clear()}
    }

}
