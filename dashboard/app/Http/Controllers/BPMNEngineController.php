<?php

namespace App\Http\Controllers;

use App\Template;
use Illuminate\Http\Request;

class BPMNEngineController extends Controller
{
    /**
     * Display a listing of the resource.
     * @return \Illuminate\Http\Response
     */
    public function launch(Request $request)
    {
        $template = Template::findOrFail($request->templateID);

        $endpoint = env("BPMN_ENGINE", "")."/flowable-rest/service/runtime/process-instances";
        
        $retryCounter =  (object)[];
        $retryCounter->name = "retryCounter";
        $retryCounter->value = $request->retryCounter;
        
        $createTimeout =  (object)[];
        $createTimeout->name = "createTimeout";
        $createTimeout->value = "PT".$request->createTimeout."M";
        
        $checkPeriod =  (object)[];
        $checkPeriod->name = "checkPeriod";
        $checkPeriod->value = "PT".$request->checkPeriod."S";        
        
        $serviceBrokerURI =  (object)[];
        $serviceBrokerURI->name = "serviceBrokerURI";
        $serviceBrokerURI->value = env("SERVICE_BROKER_URI", "");
        
        $jsonInput =  (object)[];
        $jsonInput->name = "jsonInput";
        //$jsonInput->value = $request->json_graph;
        
        $cluster = json_decode('{"name":"cluster","type":"resource","category":"cluster","requirements":{"create":[],"configure":[],"start":[]},"properties":{"platform":"'.$request->clusterPlatform.'"}}');
        $template_json = json_decode($template->json_graph);
        array_push($template_json, $cluster);
        foreach ($template_json as $el){
            if (strcmp($el->type,"du") == 0) array_push($el->requirements->create,"cluster.create");
        }
        $jsonInput->value = json_encode($template_json);
        
        // Make Post Fields Array
        $data = [
            'processDefinitionKey' => 'bpmn4tosca-overallXX',
            'variables' => [ $createTimeout, $checkPeriod, $retryCounter, $serviceBrokerURI, $jsonInput ]
        ];

        $curl = curl_init();

        curl_setopt_array($curl, array(
            CURLOPT_URL => $endpoint,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_ENCODING => "",
            CURLOPT_MAXREDIRS => 10,
            CURLOPT_TIMEOUT => 30000,
            CURLOPT_USERPWD => "rest-admin:test",
            CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
            CURLOPT_CUSTOMREQUEST => "POST",
            CURLOPT_POSTFIELDS => json_encode($data),
            CURLOPT_HTTPHEADER => array(
                // Set here requred headers
                "accept: */*",
                "accept-language: en-US,en;q=0.8",
                "content-type: application/json"
                // "user: rest-admin:test"
            )
        ));
        
        $response = curl_exec($curl);
        $err = curl_error($curl);
        
        
        curl_close($curl);

        if($response)
        {
            try 
            {
                $response = json_decode($response);
                $template->active = true;
                $template->process_id =  $response->id;
                $template->save();
                return redirect('home');
            } catch(Exception $e)
            {
                $response = json_decode($response);
                return redirect()->back()->with('error', $response->message)->withInput();
            }

        }
        else 
        {
            return redirect()->back()->with('error', $err? $err : TRUE)->withInput();
        }

    }
    
    /**
     * Store a newly created resource in storage.
     *
     * @param  \Illuminate\Http\Request  $request
     * @return \Illuminate\Http\Response
     */
    public function store(Request $request)
    {
//
    }

    /**
     * Display the specified resource.
     *
     * @param  int  $id
     * @return \Illuminate\Http\Response
     */
    public function show($id)
    {
        //
    }

    /**
     * Update the specified resource in storage.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  int  $id
     * @return \Illuminate\Http\Response
     */
    public function update(Request $request, $id)
    {
        //
    }

    /**
     * Remove the specified resource from storage.
     *
     * @param  int  $id
     * @return \Illuminate\Http\Response
     */
    public function destroy($id)
    {
        //
    }
}
