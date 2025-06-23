package com.miu.meditationapp.helper

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import java.io.InputStream
import kotlin.concurrent.thread

object DropboxHelper {
    private const val ACCESS_TOKEN = "sl.u.AFyubhh0J_jasVw-RGoxPffdyywFLwbG3E1eFh6o7iZN4HDDnpJpB9777a2iM_MQD0WgYvvg_JhvXDJBRn8eJMnLGRcdHpfj0Mb56ik8pTe8QzlU0HRlqzox8HfRLkzvlwhcKshtuezPFK4IzHTZqSQdiARY_BMBZZsvpLpnXpoz_xWdPpIkipzGC2fd0L95PmS-X9cZbAiqtfO7B0smSru6I1Ee_dP9hCBYZBwEtDvx2iTdxfcu00m0qmoZiUvvyWtjSJwnPQWDHYuMul3-zhVruKo3Tb6CtPjo3bvXzBdZAZHw4irQefSp_ulwAVxg1rWXCZxOp3eqigN5Q4uc-TjyP1cLrFpMJE9spzBZxK3NfC3XJjED49wmFe_4aK5ry06W7nhXT2ypUybkmxjPLsBznPe56NQ7LkSzbM_rYLTTtzF_aw4jUNZXcFE-gV2vUDztpjO9E1vGVSWU6NWfVDXSkfRG-fv-71ppflnDz2SvdtH-oQZa-c-HjqiDPE7r1eFeF_QHmCurYyqdPMePTRYdy8lExz1rbv2oj6UWPfnux_vPjLXrOYDSmMLIuRftSh2R0f104ZvYez8cKPGCPDRd8amC2q3XIc7Q--JQlN85i-MEBvMHTqyIQPdnjcAzWU1driuNWQuCeJYrs9kHTJGHArMPU0xpUqLTG76hOBsVmVDkeIM9ztFcxjmOB9AxVSzz4rqvYDwIPV_mPY9p_jS7Rfcflq__IujVhy4I3AotaAafEkrjt--3_ff8nCTnH_CYFldzIQx2UbWkZTI--IP4HmFQxI-5nRmsBiuT9y1S1BLQkA9XNPkrt7QX8tzU9NN_Mk12Ez2_BzASDd_piYs3uMzwDy44XqNa3dW71RRcQDg8UUNczPvndU85XOOUk5frbmAO4XrhbLYONRr89iD_EhOhtKO2LlOVvH-Grn0QppRoP-1mb5dJNKmx3_bxA4khCUCJx9Yn00Qh5ndTebzvfHGRfmIr4zlGptSzIPNtYv4zVi0SJbhuPQF4nELUNd5pcjNj5nYkY3eFVe-juJuJQ9AmLfIKz1cL7H9n4_LqytQNfQLS27Q4OfN3bP44QstxmN-0qYhIAydfZ3jaiMQVRdfNTBOKUKdB0pXNROT-pM969yJFmkHCzvIdDCk6htXtpeJzAtvvq4icalc196cZLH3rZWTojsUuCECfkY8OWuGPvt3UGoDswSqHOjhyyTGSgVeBCE6eHM2fr9Jp1xLkCSOimj-j7mNVcYky6_aImOTvXCWgRQYPT3xl2gevl6BIFubxBz3cUe62_SPifvwsYkDDGl-zbJhu_Hxc3lb5azkqz8pFR85QGUG1PvuJLvW_CwbGWsG3vIb_Nyc59fw8q2-_vKTb-fwlO38M930T9DBiD74lpljkJobHi_sPxyhwypprjfuIB5qLMhr-CYV6AoFjGlIxBEVG-nGJUfwq1A"
    private val config = DbxRequestConfig.newBuilder("meditation-app").build()
    private val client = DbxClientV2(config, ACCESS_TOKEN)

    fun uploadFile(inputStream: InputStream, dropboxPath: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        thread {
            try {
                client.files().uploadBuilder(dropboxPath).uploadAndFinish(inputStream)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getSharedLink(dropboxPath: String): String {
        return try {
            val sharedLinkMetadata = client.sharing().createSharedLinkWithSettings(dropboxPath)
            // Thay mọi dl=0 thành raw=1 (dù ở cuối hay giữa link)
            sharedLinkMetadata.url.replace("dl=0", "raw=1")
        } catch (e: Exception) {
            ""
        }
    }
}
