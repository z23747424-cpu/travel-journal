package cn.edu.traveljournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NativePlanEditingTest {
    @Test fun reopeningSavedRowRestoresUnsavedRevisionEvenIfTimestampIsEqual(){
        val saved=TravelPlan(destination="京都",notes="已保存")
        val draft=saved.copy(notes="尚未保存",days=saved.days+PlanDay())
        val opened=nativePlanForOpen(saved,draft)
        assertSame(draft,opened)
        assertEquals("尚未保存",opened.notes)
        assertEquals(2,opened.days.size)
    }

    @Test fun otherPlansDraftCannotBeSubstitutedForSelectedRow(){
        val saved=TravelPlan(destination="京都")
        assertSame(saved,nativePlanForOpen(saved,TravelPlan(destination="巴黎")))
        assertSame(saved,nativePlanForOpen(saved,null))
    }
}
