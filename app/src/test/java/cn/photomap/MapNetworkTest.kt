package cn.photomap
import cn.photomap.map.MapNetwork
import org.junit.Assert.*
import org.junit.Test

class MapNetworkTest {
    @Test fun onlyApprovedReadOnlyMapResourcesAreAllowed() {
        assertTrue(MapNetwork.allowed("GET","https","tiles.openfreemap.org","/planet",false,false))
        assertTrue(MapNetwork.allowed("GET","https","tiles.openfreemap.org","/planet/latest/14/13490/6208.pbf",false,false))
        assertTrue(MapNetwork.allowed("GET","https","tiles.maps.eox.at","/wmts/1.0.0/s2cloudless_3857/default/g/14/6208/13490.jpg",false,false))
        assertFalse(MapNetwork.allowed("POST","https","tiles.openfreemap.org","/planet",false,true))
        assertFalse(MapNetwork.allowed("GET","https","tiles.openfreemap.org","/upload/photo.jpg",false,false))
        assertFalse(MapNetwork.allowed("GET","https","other.example","/planet",false,false))
        assertFalse(MapNetwork.allowed("GET","https","tiles.openfreemap.org","/planet",true,false))
        assertFalse(MapNetwork.allowed("GET","http","tiles.openfreemap.org","/planet",false,false))
    }
}
