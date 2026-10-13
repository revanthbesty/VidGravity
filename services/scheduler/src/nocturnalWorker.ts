export interface NightlyJobOptions {enabled:boolean;windowStartHour:number;windowEndHour:number;requiresCharging:true;requiresUnmeteredNetwork:true;}
export interface SchedulerAdapter {scheduleUnique(name:string,options:NightlyJobOptions):Promise<void>;}
/** Platform adapter must enforce charging + unmetered network; this module never downloads by itself. */
export async function scheduleNocturnalCache(adapter:SchedulerAdapter,enabled:boolean):Promise<void>{
 const options:NightlyJobOptions={enabled,windowStartHour:2,windowEndHour:5,requiresCharging:true,requiresUnmeteredNetwork:true};
 await adapter.scheduleUnique("vidgravity-nocturnal-cache",options);
}
export function mayRunNocturnalCache(now:Date,isCharging:boolean,isUnmetered:boolean):boolean {const h=now.getHours();return h>=2&&h<5&&isCharging&&isUnmetered;}
